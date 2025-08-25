package com.capturerx.common.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;

class KafkaUtilTest {

    ObjectMapper mapper = new ObjectMapper();

    @Mock
    KafkaUtil kafkaUtil;

    @BeforeEach
    public void init(){
        MockitoAnnotations.openMocks(this);
        KafkaUtil.clearProcessedMsgs4Test(kafkaUtil);
    }
    private StatusMessage genStatusMessage() {
        StatusMessage msg = new StatusMessage();
        msg.setCorRelatedId(UUID.randomUUID());
        return msg;
    }

    private WorkerMessage genWorkerMessage() {
        WorkerMessage msg = new WorkerMessage();
        msg.setId(UUID.randomUUID());
        return msg;
    }

    @Test
    void test_getMessagePayloadAsObject_success() throws JsonProcessingException {
        StatusMessage statusMessage = genStatusMessage();
        Message msg = MessageBuilder.withPayload(mapper.writeValueAsString(statusMessage).getBytes(StandardCharsets.UTF_8)).build();

        KafkaUtil.setStandardMapper();
        StatusMessage payload = KafkaUtil.getMessagePayloadAsObject(msg, StatusMessage.class);

        assertEquals(statusMessage.getCorRelatedId(), payload.getCorRelatedId());
        assertTrue(payload instanceof StatusMessage);
    }
    @Test
    void test_getMessagePayloadAsObject_fail() throws JsonProcessingException {
        WorkerMessage workerMessage = genWorkerMessage();
        Message msg = MessageBuilder.withPayload(mapper.writeValueAsString(workerMessage)).build();
        KafkaUtil.setStandardMapper();
        WorkerMessage payload = KafkaUtil.getMessagePayloadAsObject(msg, WorkerMessage.class);

        assertTrue(payload instanceof WorkerMessage);
        assertEquals(workerMessage.getId(), payload.getId());
    }

    @Test
    void getHeaderValue() throws JsonProcessingException {
        WorkerMessage workerMessage = genWorkerMessage();
        Message msg = MessageBuilder.withPayload(mapper.writeValueAsString(workerMessage))
                .setHeader("key1", "value1").build();
        String headerValue = KafkaUtil.getHeaderValue(msg, "key1");
        assertEquals("value1", headerValue);
    }

    @Test
    void isHeaderValuePresent() throws JsonProcessingException {
        WorkerMessage workerMessage = genWorkerMessage();
        Message msg = MessageBuilder.withPayload(mapper.writeValueAsString(workerMessage))
                .setHeader("key1", "value1").build();
        boolean isPresent = KafkaUtil.isHeaderValuePresent("key1", msg,"value0","value1","value2");
        assertTrue(isPresent);
    }

    @Test
    void isHeaderValuePresent_fail() throws JsonProcessingException {
        WorkerMessage workerMessage = genWorkerMessage();
        Message msg = MessageBuilder.withPayload(mapper.writeValueAsString(workerMessage))
                .setHeader("key1", "value9").build();
        boolean isPresent = KafkaUtil.isHeaderValuePresent("key1", msg,"value0","value1","value2");
        assertFalse(isPresent);
    }

    @Test
    void testGetStandardHeaders() throws JsonProcessingException {
        WorkerMessage workerMessage = genWorkerMessage();
        Message msg = MessageBuilder.withPayload(mapper.writeValueAsString(workerMessage)).build();

        String headers = KafkaUtil.getStandardHeaders(msg);
        assertTrue(headers.startsWith("topic/group"));
    }

    @Test
    void test() throws JsonProcessingException {
        Logger logger = (Logger) LoggerFactory.getLogger(KafkaUtil.class.getName());
        Appender<ILoggingEvent> mockAppender = Mockito.mock(Appender.class);
        logger.addAppender(mockAppender);

        WorkerMessage workerMessage = genWorkerMessage();
        Message msg = MessageBuilder.withPayload(mapper.writeValueAsString(workerMessage)).build();

        KafkaUtil.logStdMessageHeaders(msg);

        Mockito.verify(mockAppender).doAppend(ArgumentMatchers.argThat(argument -> {
            assertThat(argument.getMessage(), containsString("Std headers:"));
            assertThat(argument.getLevel(), is(Level.INFO));
            return true;
        }));
    }
}
