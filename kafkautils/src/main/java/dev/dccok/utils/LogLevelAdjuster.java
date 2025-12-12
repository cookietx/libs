package dev.dccok.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class LogLevelAdjuster implements ApplicationContextAware {

    private String appName;
    private String channelTopic;
    private String channelGroup;
    private boolean isOk = true;
    private static final String APP_NAME_KEY = "info.app.name";
    private static final String DESTINATION_KEY = "spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.destination";
    private static final String DESTINATION_TOPIC = "log_level_adjustment";
    private static final String GROUP_KEY = "spring.cloud.stream.bindings.logLevelAdjusterChannel-in-0.group";

    private static final String ERR_FMT_MSG = """
            Properties for enabling log level adjustments are missing.  Confirm the presence of properties:
            {} = <application name>
            {} = {}
            {} = {}_<application name>
            """;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ConfigurableEnvironment environment = (ConfigurableEnvironment) applicationContext.getEnvironment();
        appName = environment.getProperty(APP_NAME_KEY);
        channelTopic = environment.getProperty(DESTINATION_KEY);
        channelGroup = environment.getProperty(GROUP_KEY);
    }

    public boolean enableLogLevelAdjustment() {

        if (!StringUtils.hasText(appName) || !DESTINATION_TOPIC.equals(channelTopic)
                || !StringUtils.hasText(channelGroup) || !channelGroup.endsWith(appName)
                || !channelGroup.startsWith(DESTINATION_TOPIC)) {
            isOk =  false;
            log.error(ERR_FMT_MSG, APP_NAME_KEY, DESTINATION_KEY, DESTINATION_TOPIC, GROUP_KEY, DESTINATION_TOPIC);
        }
        log.info("adjustment enabled");
        return isOk;
    }

    @Bean
    public Consumer<Message<?>> logLevelAdjusterChannel() {
        return incomingMessage -> {
            log.info("message accepted ok=" +isOk);
            if (isOk) {
                LogLevelAdjustmentMessage message = KafkaUtil.getMessagePayloadAsObject(incomingMessage, LogLevelAdjustmentMessage.class);
                if (StringUtils.hasText(message.getApplicationName()) && !message.getApplicationName().equals(appName)) {
                    log.debug("Logger not changed, not for this application");
                } else {
                    log.info("logLevelAdjusterChannel received {}", message);
                    KafkaUtil.logStdMessageHeaders(incomingMessage, log);
                    if (KafkaUtil.isDupMessage(incomingMessage)) {
                        log.error("Ignoring duplicate message in logLevelAdjusterChannel");
                        return;
                    }

                    this.LogLevelAdjustmentMessage(message);
                    KafkaUtil.commit(incomingMessage, log);
                }
            }
        };
    }
    void LogLevelAdjustmentMessage(LogLevelAdjustmentMessage message) {

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger =
                message.getLoggerName().equalsIgnoreCase("root") ?
                        loggerContext.getLogger(message.getLoggerName()) : loggerContext.exists(message.getLoggerName());
        if( logger !=null){
            logger.setLevel(Level.toLevel(message.getLogLevel()));
            log.info("Changed logger: {} to level {} ", message.getLoggerName(), message.getLogLevel());
        } else {
            log.error("Logger {} Not Found Make Sure that logger name is correct", message.getLoggerName());
        }
    }
}
