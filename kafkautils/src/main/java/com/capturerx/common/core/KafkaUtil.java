package com.capturerx.common.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@Getter
public class KafkaUtil implements ApplicationContextAware {

    public static final String TWO_STRS = "%s/%s";
    public static final String TWO_DIGS = "%d/%d";

    @Autowired
    private StreamBridge streamBridge;

    private static KafkaUtil INSTANCE;

    @AllArgsConstructor
    @Getter
    private static class OutboundMessage {
        String bindingName;
        Message data;
    }

    private final List<OutboundMessage> pendingOutboundMessages = new ArrayList<>();

    private void sendPendingMessages() {
        if (getPendingOutboundMessages().size() > 0) {
            log.info("Posting {} pending outbound Kafka messages", getPendingOutboundMessages().size());
            for(OutboundMessage pendingMsg : getPendingOutboundMessages()) {
                if (!send(pendingMsg.getBindingName(), pendingMsg.getData()))
                    log.error("Failed to send pending Kafka msg to {}", pendingMsg.getBindingName());
            }
            getPendingOutboundMessages().clear();
        }
    }
    private void clearPendingMessage() {
        if (getPendingOutboundMessages().size() > 0) {
            log.warn("Clearing {} unsent outbound Kafka messages", pendingOutboundMessages.size());
            getPendingOutboundMessages().clear();
        }
    }

    public void sendAtCommit(String bindingName, Message data) {
        Message dup = null;
        try {
            Class claz = data.getPayload().getClass();
            String payload = mapper.writeValueAsString(data.getPayload());
            Object obj = mapper.readValue(payload, claz);
            dup = MessageBuilder.createMessage(obj, data.getHeaders());
        } catch (JsonProcessingException e) {
            log.error("Failed to copy payload", e);
            throw new RuntimeException(e);
        }
        getPendingOutboundMessages().add(new OutboundMessage(bindingName, dup));
    }

    public boolean send(String bindingName, Message data) {
        String[] channel = bindingName.split("-");
        data = MessageBuilder.fromMessage(data).setHeader("producerid", channel[0]).build();
        msgTopics.add(channelTopicMap.get(bindingName));
        return streamBridge.send(bindingName, data);
    }

    public void logProducedMsgTopics(Logger logger) {
        if (!msgTopics.isEmpty()) {
            logger.info("Std msgs produced on {}", msgTopics);
        }
    }

    private static long maxProcessingTimeMs = 300000L;
    private static ObjectMapper mapper;
    private final String OM = "objectMapper";

    private static final Map<String,String> processedMsgs = new HashMap<>();

    private static Map<String, Long> processStartTimeMap = new HashMap<>();
    private static Map<String, String> channelTopicMap = new HashMap<>();
    private static Set<String> msgTopics = new HashSet<>();
    private static String appName= "unknown";

    public static void clearProcessedMsgs4Test(KafkaUtil mock) {
        INSTANCE = mock;
        clearProcessedMsgs4Test();
    }
    public static void clearProcessedMsgs4Test() {
        log.info("clearing processedMsgs");
        processedMsgs.clear();
        msgTopics.clear();
    }

    private static String getMessageKey(Message msg) {
        MessageHeaders headers = msg.getHeaders();
        String topic = (String) headers.get(KafkaHeaders.RECEIVED_TOPIC);
        String group = (String) headers.get(KafkaHeaders.GROUP_ID);
        return String.format(TWO_STRS,topic, group);
    }
    private static void recordStartTime(Message msg) {
        INSTANCE.clearPendingMessage();
        msgTopics.clear();
        processStartTimeMap.put(getMessageKey(msg), System.currentTimeMillis());
    }

    private static long checkForOvertime(Message msg, Logger logger) {
        long startedAt = processStartTimeMap.get(getMessageKey(msg));
        long duration = System.currentTimeMillis() - startedAt;
        if (duration > maxProcessingTimeMs) {
            logger.error("Message processing took to long, this will cause duplicate processing.");
        }
        return duration;
    }

    public static boolean isDupMessage(Message msg) {
        MessageHeaders headers = msg.getHeaders();
        String key = String.format(TWO_STRS,headers.get(KafkaHeaders.RECEIVED_TOPIC),headers.get(KafkaHeaders.GROUP_ID));
        String val = String.format(TWO_DIGS,(Integer) headers.get(KafkaHeaders.RECEIVED_PARTITION),(Long) headers.get(KafkaHeaders.OFFSET));
        String prev = processedMsgs.get(key);
        return prev != null && prev.equals(val);
    }

    public static void commit(Message msg) { commit(msg, log); }
    public static void commit(Message msg, Logger logger) {
        INSTANCE.sendPendingMessages();
        INSTANCE.logProducedMsgTopics(logger);
        String prefix = "Std commit (auto):";
        Acknowledgment acknowledgment = msg.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
            prefix = "Std commit (man ack):";
        }
        long duration = checkForOvertime(msg, logger);
        logger.info("{} {}ms {}", prefix, Long.toString(duration), getStandardHeaders(msg));
        // recording as processed
        MessageHeaders headers = msg.getHeaders();
        String key = String.format(TWO_STRS,headers.get(KafkaHeaders.RECEIVED_TOPIC),headers.get(KafkaHeaders.GROUP_ID));
        String val = String.format(TWO_DIGS,headers.get(KafkaHeaders.RECEIVED_PARTITION),(Long) headers.get(KafkaHeaders.OFFSET));
        processedMsgs.put(key,val);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        INSTANCE = this;
        if (Arrays.asList(applicationContext.getBeanDefinitionNames()).contains(OM)) {
            mapper = (ObjectMapper) applicationContext.getBean(OM);
        } else {
            setStandardMapper();
        }
        String maxPollTimeout = applicationContext.getEnvironment()
                .getProperty("spring.kafka.consumer.properties.max.poll.interval.ms");
        if (StringUtils.hasText(maxPollTimeout)) {
            maxProcessingTimeMs = Long.valueOf(maxPollTimeout);
        }  else {
            log.warn("spring.kafka.consumer.properties.max.poll.interval.ms not defined, value 300000 assumed");
        }
        try {
            Resource resource = new ClassPathResource("application.properties");
            Properties props = PropertiesLoaderUtils.loadProperties(resource);
            for(Object key : props.keySet()) {
                String keyStr = key.toString();
                if (keyStr.contains("-out-0.destination")) {
                    String[] parts = keyStr.split("\\.");
                    channelTopicMap.put(parts[4], applicationContext.getEnvironment().getProperty(keyStr));
                }
                else if (keyStr.equals("info.app.name")) {
                    appName = (String) props.get(key);
                }
            }
        } catch (Exception e) {
            log.error("Unable to access application.properties");
        }
    }

    public static void setStandardMapper() {
        mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(new JavaTimeModule());
    }

    // extract payload String  from Message
    public static String getMessagePayloadAsString(Message msg) {
        recordStartTime(msg);
        Object plObj = msg.getPayload();
        if (plObj instanceof String string) return string;
        if (plObj instanceof byte[] bytes) return new String(bytes);
        return plObj.toString();
    }

    public static <T> T getMessagePayloadAsObject(Message msg, Class <T> claz) {
        try {
            String desired = claz.getName();
            String actual = msg.getPayload().getClass().getName();
            if (desired.equals(actual)) {
                recordStartTime(msg);
                return (T) msg.getPayload();
            }
            return mapper.readValue(getMessagePayloadAsString(msg), claz);
        } catch (JsonProcessingException e) {
            log.error(String.format("Unable to map message %s to %s", msg, claz.getSimpleName()),e);
            return null;
        }
    }

    public static String getHeaderValue(Message msg, String key) {
        Object obj = msg.getHeaders().get(key);
        if (null == obj) return null;
        if (obj instanceof byte[] bytes) return new String(bytes);
        return obj.toString();
    }

    public static boolean isHeaderValuePresent(String key, Message msg, String... value) {
        return isHeaderValuePresent(null, key, msg, value);
    }
    public static boolean isHeaderValuePresent(Logger log, String key, Message msg, String... value) {
        if (null == key || null == value) return false;
        String flagValue = getHeaderValue(msg, key);

        if (null == flagValue) return false;
        for(String v : value) {
            if (v.equals(flagValue)) {
                if (log != null) {
                    log.info("Selected on header key={}, value={}",key, v);
                }
                return true;
            }
        }
        return false;
    }


    public static void logStdMessageHeaders(Message msg) {
        logStdMessageHeaders(msg, log);
    }

    public static void logStdMessageHeaders(Message msg, Logger logger) {
        logger.info("Std headers: {}", getStandardHeaders(msg));
    }
    public static String getStandardHeaders(Message msg) {
        MessageHeaders headers = msg.getHeaders();
        String topic = (String) headers.get(KafkaHeaders.RECEIVED_TOPIC);
        String group = (String) headers.get(KafkaHeaders.GROUP_ID);
        Integer part = (Integer) headers.get(KafkaHeaders.RECEIVED_PARTITION);
        Long off = (Long) headers.get(KafkaHeaders.OFFSET);
        Long tmstp = (Long) headers.get(KafkaHeaders.RECEIVED_TIMESTAMP);
        AtomicInteger atmpt = (AtomicInteger) headers.get("deliveryAttempt");
        return String.format("topic/group: %s/%s, part/off: %d/%d, produced: %s, attempt: %d",
                topic, group, part, off, tmstp, atmpt != null ? atmpt.get() : -1);
    }
}
