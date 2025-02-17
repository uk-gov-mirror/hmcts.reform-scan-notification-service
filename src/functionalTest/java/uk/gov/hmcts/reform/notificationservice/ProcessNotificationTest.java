package uk.gov.hmcts.reform.notificationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.microsoft.azure.servicebus.IQueueClient;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.notificationservice.data.NotificationStatus.SENT;

@Disabled
class ProcessNotificationTest {

    @Test
    void should_process_message_from_queue_and_notify_provider() throws InterruptedException, ServiceBusException {
        // given
        IQueueClient queueClient = Configuration.getSendClient();
        var messageDetails = QueueMessageHelper.getQueueMessageDetails("valid-notification.json");

        // when
        queueClient.send(new Message(
            messageDetails.messageId,
            messageDetails.messageBody,
            messageDetails.contentType
        ));

        // and
        String serviceAuthToken = RestAssuredHelper.s2sSignIn(messageDetails.service);

        // then
        await("Error notification has been processed for file " + messageDetails.zipFileName)
            .atMost(1L, TimeUnit.MINUTES)
            .pollDelay(1L, TimeUnit.SECONDS)
            .pollInterval(5L, TimeUnit.SECONDS)
            .until(
                () -> RestAssuredHelper.getNotification(serviceAuthToken, messageDetails.zipFileName),
                jsonNode -> testNotification(jsonNode, messageDetails)
            );
    }

    private boolean testNotification(JsonNode jsonNode, QueueMessageDetails messageDetails) {
        assertThat(jsonNode.get("notifications").getNodeType()).isEqualTo(JsonNodeType.ARRAY);
        assertThat(jsonNode.get("notifications"))
            .as("Notification details for file %s", messageDetails.zipFileName)
            .hasSize(1)
            .first()
            .satisfies(node -> {
                assertThat(Long.valueOf(node.get("id").asText())).isGreaterThan(0);
                assertThat(node.get("confirmation_id").asText()).isNotEmpty();
                assertThat(node.get("zip_file_name").asText()).isEqualTo(messageDetails.zipFileName);
                assertThat(node.get("service").asText()).isEqualTo(messageDetails.service);
                assertThat(node.get("processed_at").textValue()).isNotEmpty();
                assertThat(node.get("status").asText()).isEqualTo(SENT.name());
            });

        return true; // all satisfied
    }
}
