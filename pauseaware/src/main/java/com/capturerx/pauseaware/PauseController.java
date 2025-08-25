package com.capturerx.pauseaware;

import com.capturerx.featureflags.FeatureFlags;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.FlagTracker;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PauseController implements ApplicationContextAware {

    public static final String APP_NAME = "info.app.name";
    @Autowired
    private FeatureFlags featureFlags;
    private FlagTracker flagTracker;

    private ApplicationContext ctx;

    public static final String GLOBAL_PAUSE_KEY = "global.pause";
    private String APP_PAUSE_KEY;
    public static final String GLOBAL_PAUSE_INTERVAL_KEY = "global.pause.interval";
    private String APP_PAUSE_INTERVAL_KEY;

    private int globalPauseIntervalMs = 0;
    private int appPauseIntervalMs = 0;

    private boolean isGlobalPause;
    private Thread pausedThread;

    private FlagChangeListener globalPauseListener;
    private FlagChangeListener globalIntervalListener;
    private FlagChangeListener appPauseListener;
    private FlagChangeListener appIntervalListener;

    private static boolean shutdown;

    public static boolean isShutdown() {
        return shutdown;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ctx = applicationContext;
        String appName = ctx.getEnvironment().getProperty(APP_NAME);
        APP_PAUSE_KEY = appName + ".pause";
        APP_PAUSE_INTERVAL_KEY = appName + ".pause.interval";

        LDClientInterface ldClient = featureFlags.getLDClient();
        flagTracker = ldClient.getFlagTracker();
        watchForIntervalChange();
        if (checkPauseAtStartup()) {
            try {
                pauseB4Shutdown();
            } catch (InterruptedException e) {
                log.warn("sleep interrupted");
            }
        } else {
            watchForPauseFlagChange();
        }
    }

    boolean checkPauseAtStartup() {
        // check for global pause
        boolean paused = featureFlags.getClient().getBooleanVariation(GLOBAL_PAUSE_KEY, false);
        if (paused) {
            isGlobalPause = true;
        } else if (featureFlags.getLDClient().isFlagKnown(APP_PAUSE_KEY)) {
            paused = featureFlags.getClient().getBooleanVariation(APP_PAUSE_KEY, false);
        }
        return paused;
    }

    void watchForPauseFlagChange() {

        globalPauseListener = flagTracker.addFlagValueChangeListener(GLOBAL_PAUSE_KEY, featureFlags.getUser(), flagValueChangeEvent -> {
            // checking change to global pause interval and terminate pausing when needed
            LDValue val = flagValueChangeEvent.getNewValue();
            if (val.getType() == LDValueType.BOOLEAN) {
                boolean bval = val.booleanValue();
                if (!bval) {
                    if (pausedThread != null) {
                        pausedThread.interrupt();
                    }
                } else {
                    log.info("global flag change, need shutdown now");
                    shutdownNow();
                }
                isGlobalPause = true;
            }
        });

        appPauseListener =flagTracker.addFlagValueChangeListener(APP_PAUSE_KEY, featureFlags.getUser(),  flagValueChangeEvent -> {
            // checking change to application pause interval and terminate pausing when needed
            if (pausedThread == null || !isGlobalPause) {
                LDValue val = flagValueChangeEvent.getNewValue();
                if (val.getType() == LDValueType.BOOLEAN) {
                    boolean bval = val.booleanValue();
                    if (!bval) {
                        if (pausedThread != null && !isGlobalPause) {
                            pausedThread.interrupt();
                        }
                    } else {
                        if (!isGlobalPause) {
                            log.info("app flag change, need shutdown now");
                            shutdownNow();
                        }
                    }
                }
            }
        });
    }

    void watchForIntervalChange() {
        globalPauseIntervalMs = featureFlags.getClient().getIntegerVariation(GLOBAL_PAUSE_INTERVAL_KEY, globalPauseIntervalMs);
        if (featureFlags.getLDClient().isFlagKnown(APP_PAUSE_INTERVAL_KEY)) {
            appPauseIntervalMs = featureFlags.getClient().getIntegerVariation(APP_PAUSE_INTERVAL_KEY, globalPauseIntervalMs);
        }

         globalIntervalListener = flagTracker.addFlagValueChangeListener(GLOBAL_PAUSE_INTERVAL_KEY, featureFlags.getUser(), flagValueChangeEvent -> {
            // checking change to global pause interval and terminate pausing when needed
            LDValue val = flagValueChangeEvent.getNewValue();
            if (val.getType() == LDValueType.NUMBER) {
                int nval = val.intValue();
                if (pausedThread != null && isGlobalPause && nval > globalPauseIntervalMs) {
                    log.info("global interval change, need shutdown now");
                    shutdownNow();
                }
                globalPauseIntervalMs = nval;
            }
        });

        appIntervalListener =flagTracker.addFlagValueChangeListener(APP_PAUSE_INTERVAL_KEY, featureFlags.getUser(), flagValueChangeEvent -> {
            // checking change to application pause interval and terminate pausing when needed
            LDValue val = flagValueChangeEvent.getNewValue();
            if (val.getType() == LDValueType.NUMBER) {
                int nval = val.intValue();
                if (pausedThread != null && !isGlobalPause && nval > appPauseIntervalMs) {
                    log.info("app interval change, need shutdown now");
                    shutdownNow();
                }
                appPauseIntervalMs = nval;
            }
        });

    }

    void shutdownNow() {
        shutdown = true;
        if (pausedThread != null) {
            pausedThread.interrupt();
        }
        shutdown();
    }

    void pauseB4Shutdown() throws InterruptedException {
        shutdown = true;
        long interval = isGlobalPause ? globalPauseIntervalMs : appPauseIntervalMs;
        log.warn(String.format("%s pause detected, %d ms delay before termination", (isGlobalPause ? "Global" : "Application"), interval));

        pausedThread = Thread.currentThread();
        Thread.sleep(isGlobalPause ? globalPauseIntervalMs : appPauseIntervalMs);
        shutdown();
    }

    synchronized void shutdown() {
        log.warn("in shutdown");
        if (globalPauseListener != null) {
            flagTracker.removeFlagChangeListener(globalPauseListener);
        }
        if (appPauseListener != null) {
            flagTracker.removeFlagChangeListener(appPauseListener);
        }
        if (globalIntervalListener != null) {
            flagTracker.removeFlagChangeListener(globalIntervalListener);
        }
        if (appIntervalListener != null) {
            flagTracker.removeFlagChangeListener(appIntervalListener);
        }
        featureFlags.shutdown();
    }
}
