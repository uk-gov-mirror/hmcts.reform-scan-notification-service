package uk.gov.hmcts.reform.notificationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.microsoft.azure.servicebus.MessageBody;
import uk.gov.hmcts.reform.notificationservice.service.NotificationMessageParser;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;

final class QueueMessageHelper {

    private static final String FILENAME_SUFFIX_SEARCH = "{suffix}";
    private static final NotificationMessageParser MESSAGE_PARSER = new NotificationMessageParser(new ObjectMapper());

    private QueueMessageHelper() {
        // utility class construct
    }

    static QueueMessageDetails getQueueMessageDetails(String resourceFileName) {
        var rawContent = fileContentAsBytes("servicebus/message/" + resourceFileName);
        var messageBody = MessageBody.fromBinaryData(singletonList(rawContent));
        var notificationMessage = MESSAGE_PARSER.parse(messageBody);

        return new QueueMessageDetails(
            randomUUID().toString(),
            messageBody,
            notificationMessage.zipFileName,
            notificationMessage.service
        );
    }

    public static byte[] fileContentAsBytes(String file) {
        try {
            return Resources
                .toString(getResource(file), Charset.defaultCharset())
                .replace(FILENAME_SUFFIX_SEARCH, Long.toString(System.currentTimeMillis()))
                .getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Could not load file: " + file, e);
        }
    }
}
