package com.capturerx.common.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// For the LogLevelAdjuster class, this file contains the LogLevelAdjusterTest class,
// which tests the 'setApplicationContext' method. The purpose of the 'setApplicationContext' 
// method is to set the application context.
class LogLevelAdjusterTest {

    @Mock
    KafkaUtil streamBridge;

    @InjectMocks
    LogLevelAdjuster logLevelAdjuster;

    private ListAppender<ILoggingEvent> appender;
    private Logger appLogger = (Logger) LoggerFactory.getLogger(LogLevelAdjuster.class);

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        KafkaUtil.clearProcessedMsgs4Test(streamBridge);
        KafkaUtil.setStandardMapper();
        appender = new ListAppender<>();
        appender.start();
        appLogger.addAppender(appender);
    }


    private boolean logContains(String expected) {
        AtomicBoolean result = new AtomicBoolean(false);
        appender.list.forEach(event -> result.set(result.get() || event.getFormattedMessage().contains(expected)));
        return result.get();
    }

    void resetAppender() {
        appender.stop();
        appLogger.detachAppender(appender);

        appender = new ListAppender<>();
        appender.start();
        appLogger.addAppender(appender);
    }

    @AfterEach
    void teardown() {
        appender.stop();
    }

    private ApplicationContext setupEnvBaseForTest() {
        // Creating a mock ApplicationContext AND Environment
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
        // When getEnvironment of ApplicationContext is called, then return the mock Environment
        when(applicationContext.getEnvironment()).thenReturn(environment);
        return applicationContext;
    }

    @Test
    void testEnableLogLevelAdjustment_success(){
        ApplicationContext appCtx = setupEnvBaseForTest();
        ConfigurableEnvironment env = (ConfigurableEnvironment) appCtx.getEnvironment();
        when(env.getProperty("info.app.name")).thenReturn("ValidApp");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.destination")).thenReturn("log_level_adjustment");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.group")).thenReturn("log_level_adjustment_ValidApp");
        logLevelAdjuster.setApplicationContext(appCtx);

        assertTrue(logLevelAdjuster.enableLogLevelAdjustment());
    }

    @Test
    void testEnableLogLevelAdjustment_fail(){
        ApplicationContext appCtx = setupEnvBaseForTest();
        ConfigurableEnvironment env = (ConfigurableEnvironment) appCtx.getEnvironment();
        when(env.getProperty("info.app.name")).thenReturn(null);
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.destination")).thenReturn("log_level_adjustment");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.group")).thenReturn("log_level_adjustment_ValidApp");
        logLevelAdjuster.setApplicationContext(appCtx);

        assertFalse(logLevelAdjuster.enableLogLevelAdjustment());
        assertTrue(logContains("Properties for enabling log level adjustments are missing"));

        resetAppender();

        when(env.getProperty("info.app.name")).thenReturn("ValidApp");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.destination")).thenReturn(null);

        assertFalse(logLevelAdjuster.enableLogLevelAdjustment());
        assertTrue(logContains("Properties for enabling log level adjustments are missing"));

        resetAppender();

        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.destination")).thenReturn("wrong_topic");

        assertFalse(logLevelAdjuster.enableLogLevelAdjustment());
        assertTrue(logContains("Properties for enabling log level adjustments are missing"));

        resetAppender();

        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.destination")).thenReturn("log_level_adjustment");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.group")).thenReturn(null);

        assertFalse(logLevelAdjuster.enableLogLevelAdjustment());
        assertTrue(logContains("Properties for enabling log level adjustments are missing"));

        resetAppender();

        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.group")).thenReturn("log_level_adjustment");

        assertFalse(logLevelAdjuster.enableLogLevelAdjustment());
        assertTrue(logContains("Properties for enabling log level adjustments are missing"));

        resetAppender();

        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.group")).thenReturn("wrong_group");

        assertFalse(logLevelAdjuster.enableLogLevelAdjustment());
        assertTrue(logContains("Properties for enabling log level adjustments are missing"));
    }

    @Test
    void test_logLevelAdjusterChannel_success() throws JsonProcessingException {

        ApplicationContext appCtx = setupEnvBaseForTest();
        ConfigurableEnvironment env = (ConfigurableEnvironment) appCtx.getEnvironment();
        when(env.getProperty("info.app.name")).thenReturn("ValidApp");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.destination")).thenReturn("log_level_adjustment");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.group")).thenReturn("log_level_adjustment_ValidApp");
        logLevelAdjuster.setApplicationContext(appCtx);

        logLevelAdjuster.enableLogLevelAdjustment();

        LogLevelAdjustmentMessage message = new LogLevelAdjustmentMessage("ValidApp","root","DEBUG");
        ObjectMapper mapper = new ObjectMapper();
        Message msg = MessageBuilder.withPayload(mapper.writeValueAsString(message)).build();

        logLevelAdjuster.logLevelAdjusterChannel().accept(msg);
        assertTrue(logContains("Changed logger: root to level DEBUG"));

        logLevelAdjuster.logLevelAdjusterChannel().accept(msg); // this covers case of dup msg
        assertTrue(logContains("Ignoring duplicate message"));
    }

    @Test
    void test_logLevelAdjusterChannel_notForApp() throws JsonProcessingException {

        ApplicationContext appCtx = setupEnvBaseForTest();
        ConfigurableEnvironment env = (ConfigurableEnvironment) appCtx.getEnvironment();
        when(env.getProperty("info.app.name")).thenReturn("ValidApp");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.destination")).thenReturn("log_level_adjustment");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.group")).thenReturn("log_level_adjustment_ValidApp");
        logLevelAdjuster.setApplicationContext(appCtx);

        logLevelAdjuster.enableLogLevelAdjustment();

        LogLevelAdjustmentMessage message = new LogLevelAdjustmentMessage("InvalidApp","root","DEBUG");
        ObjectMapper mapper = new ObjectMapper();
        Message msg = MessageBuilder.withPayload(mapper.writeValueAsString(message)).build();

        logLevelAdjuster.logLevelAdjusterChannel().accept(msg);
        assertTrue(logContains("Logger not changed, not for this application"));

    }

    @Test
    void test_logLevelAdjusterChannel_loggerNotFound() throws JsonProcessingException {

        ApplicationContext appCtx = setupEnvBaseForTest();
        ConfigurableEnvironment env = (ConfigurableEnvironment) appCtx.getEnvironment();
        when(env.getProperty("info.app.name")).thenReturn("ValidApp");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.destination")).thenReturn("log_level_adjustment");
        when(env.getProperty("spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.group")).thenReturn("log_level_adjustment_ValidApp");
        logLevelAdjuster.setApplicationContext(appCtx);

        logLevelAdjuster.enableLogLevelAdjustment();

        LogLevelAdjustmentMessage message = new LogLevelAdjustmentMessage("ValidApp","junk","DEBUG");
        ObjectMapper mapper = new ObjectMapper();
        Message msg = MessageBuilder.withPayload(mapper.writeValueAsString(message)).build();

        logLevelAdjuster.logLevelAdjusterChannel().accept(msg);
        assertTrue(logContains("Logger junk Not Found Make Sure that logger name is correct"));

    }
}
