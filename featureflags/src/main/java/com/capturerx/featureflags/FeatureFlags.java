package com.capturerx.featureflags;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.integrations.FileData;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;


@Configuration
@Service
public class FeatureFlags implements  ApplicationContextAware {
    public static final long INIT_DELAY = 5000L;
    public static final long RECUR_DELAY = 10000L;
    public static final String LD_SDK_KEY = "LD-SDK-KEY";
    public static final String LD_CONFIG_PATHS = "ldconfig.paths";
    public static final String USING_LOCAL_CONFIG = "USING LOCAL CONFIG FILES";
    public static final String STATE_ERROR_MSG = "Feature flag datasource connection failure, state %s";
    public static final String DEFAULT_USER_KEY = "cumulus4processworkers";
    public static final String DETAIL_LOGGING_KEY = "FF.log.details";
    private static final String DETAIL_RESULT_MSG = "FF request key:%s, for:%s, result: %s";
    public static final String APP_NAME = "info.app.name";


    private Logger log = LoggerFactory.getLogger(this.getClass());

    private final ConfigurableEnvironment env;

    private DataSourceListener dataSourceListener;
    private LDClientInterface ldClient;
    private LDContext ldUser;
    LDConfig ldConfig;
    private String appName;
    private ApplicationContext ctx;
    private boolean closing;

    protected
    void setOverrideLogger(Logger logger) { // for unit testing
        if (logger != null)
            this.log = logger;
    }
    public FeatureFlags(ConfigurableEnvironment env) {
        this.env = env;
    }

    @PreDestroy
    public void closeClient() {
        closing = true;
        if (ldClient != null) {
            try {
                if (dataSourceListener != null) {
                    ldClient.getDataSourceStatusProvider().removeStatusListener(dataSourceListener);
                }
                ldClient.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private Timer loggingTimer;
    private ErrorMsgLoggerTask loggingTask;


    Timer createLoggingTimer() {
        return new Timer();
    }

    synchronized ErrorMsgLoggerTask createErrorMsgLoggerTask() {
        if (loggingTask == null) {
            loggingTask = new ErrorMsgLoggerTask();
        }
        return loggingTask;
    }

    synchronized void cancelLoggingTask() {
        if (loggingTask != null) {
            loggingTask.cancel();
            loggingTask = null;
        }
    }
    class ErrorMsgLoggerTask extends TimerTask{
        @Override
        public void run() {
            DataSourceStatusProvider.State dataSourceState = getLDClient().getDataSourceStatusProvider().getStatus().getState();
            if (DataSourceStatusProvider.State.VALID.equals(dataSourceState)) {
                cancelLoggingTask();
                return;
            }
            if (!closing) {
                log.error(String.format(STATE_ERROR_MSG, dataSourceState));
            }
        }
    }

    DataSourceListener createdDataSourceListener() {
        if (dataSourceListener == null) {
            dataSourceListener = new DataSourceListener();
        }
        return dataSourceListener;
    }
    class DataSourceListener implements DataSourceStatusProvider.StatusListener {
        @Override
        public void dataSourceStatusChanged(DataSourceStatusProvider.Status status) {
            DataSourceStatusProvider.State dataSourceState = status.getState();
            if (!DataSourceStatusProvider.State.VALID.equals(dataSourceState) && !closing) {
                log.error(String.format(STATE_ERROR_MSG, dataSourceState));
                synchronized (this) {
                    if (loggingTask == null) {
                        loggingTask = createErrorMsgLoggerTask();
                        if (loggingTimer == null) {
                            loggingTimer =createLoggingTimer();
                        }
                        loggingTimer.schedule(loggingTask, INIT_DELAY, RECUR_DELAY);
                    }
                }
            }
        }
    }

    LDClientInterface createLDClient(String sdkKey) {
        if (ldConfig == null) {
            return new LDClient(sdkKey);
        }
        else {
            return new LDClient("sdk key", ldConfig);
        }
    }

    LDContext createLDUser(String key) {
        return LDContext.create(ContextKind.DEFAULT, key);
    }


    void setLDConfigFilesPaths(String paths) {
        String[] pathArray = paths.split(", ");
        ldConfig = new LDConfig.Builder()
                .dataSource(
                        FileData.dataSource()
                                .filePaths(pathArray)
                                .autoUpdate(true)
                )
                .events(Components.noEvents())
                .build();
    }

    String getSdkKey() {
        String paths = env.getProperty(LD_CONFIG_PATHS);
        if (Strings.isNotBlank(paths)) {
            setLDConfigFilesPaths(paths);
            log.warn(String.format("%s %s", USING_LOCAL_CONFIG,paths));
            return USING_LOCAL_CONFIG;
        }
        else {
            return env.getProperty(LD_SDK_KEY);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ctx = applicationContext;
        loggingTimer = createLoggingTimer();
        String sdkKey = getSdkKey();
        if (sdkKey == null) {
            log.error("Closing application, no key or configuration found for Launch Darkly");
            shutdown();
            return;
        }
        ldClient = createLDClient(sdkKey);
        if (ldClient.isInitialized()) {
            log.info(String.format("SDK successfully initialized using key %s", sdkKey));
            ldClient.getDataSourceStatusProvider().addStatusListener(createdDataSourceListener());
        } else {
            log.error(String.format("Closing application, initialize Launch Darkly communications with key %s", sdkKey));
            shutdown();
            return;
        }
        ldUser = createLDUser(DEFAULT_USER_KEY);
        appName = env.getProperty(APP_NAME);
    }

    public synchronized void shutdown() {
        closing = true;
        new Thread(() -> {
            try {
                ((ConfigurableApplicationContext) ctx).close();
            } catch (Exception t) {
                //ignore
            }
        }).start();
    }

    public LDClientInterface getLDClient() {
        return ldClient;
    }

    public synchronized LDContext getUser() {
        if (ldUser == null) {
            ldUser = createLDUser(DEFAULT_USER_KEY);
        }
        LDContext user = ldUser;
        // if there is a username in the MDC use it for the email attribute
        String username = MDC.get("username");
        if (username != null && !username.isEmpty()) {
            user = createLDUser(username);
        }
        return user;
    }

    @Bean(name = "FFClient")
    public Client getClient() {
        return new Client();
    }

    public class Client implements FFClient {

        @Override
        public String prefixAppName(String key) {
            if (Strings.isBlank(appName)) {
                return key;
            }
            return String.format("%s.%s", appName, key);
        }

        @Override
        public String getStringVariation(String key, String dflt) {
            LDContext user = getUser();
            EvaluationDetail<String> detail = getLDClient().stringVariationDetail(key, user, dflt);
            testForLogging(String.format(DETAIL_RESULT_MSG, key, user, detail), detail.toString());
            return detail.getValue();
        }

        @Override
        public Integer getIntegerVariation(String key, Integer dflt) {
            LDContext user = getUser();
            EvaluationDetail<Integer> detail = getLDClient().intVariationDetail(key, user, dflt);
            testForLogging(String.format(DETAIL_RESULT_MSG, key, user, detail), detail.toString());
            return detail.getValue();
        }

        @Override
        public Double getDoubleVariation(String key, Double dflt) {
            LDContext user = getUser();
            EvaluationDetail<Double> detail = getLDClient().doubleVariationDetail(key, user, dflt);
            testForLogging(String.format(DETAIL_RESULT_MSG, key, user, detail), detail.toString());
            return detail.getValue();
        }

        @Override
        public Boolean getBooleanVariation(String key, Boolean dflt) {
            LDContext user = getUser();
            EvaluationDetail<Boolean> detail = getLDClient().boolVariationDetail(key, user, dflt);
            testForLogging(String.format(DETAIL_RESULT_MSG, key, user, detail), detail.toString());
            return detail.getValue();
        }

        private void testForLogging(String msg, String reason) {
            if (reason.contains("ERROR")) {
                log.error(msg);
            }
            else if (getLDClient().boolVariation(DETAIL_LOGGING_KEY, ldUser,false)) {
                log.info(msg);
            }
        }
    }
}
