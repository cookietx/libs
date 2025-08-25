package com.capturerx.pauseaware;

import com.capturerx.featureflags.FFClient;
import com.capturerx.featureflags.FeatureFlags;

import com.launchdarkly.sdk.LDValue;

import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent;
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeListener;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import com.launchdarkly.sdk.server.interfaces.FlagTracker;
import com.launchdarkly.shaded.com.google.gson.stream.JsonWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PauseControllerTest {

    @Mock
    ConfigurableEnvironment env;

    @Mock
    ApplicationContext applicationContext;

    @Mock
    ConfigurableEnvironment configurableEnvironment;

    @Mock
    FeatureFlags featureFlags;

    @Mock
    FeatureFlags.Client ffClient;

    @Mock
    LDClientInterface ldClient;

    @Mock
    FlagTracker flagTracker;

    @InjectMocks
    PauseController pauseController;

    @BeforeEach
    void init() {
        doReturn(env).when(applicationContext).getEnvironment();
        doReturn("Test").when(env).getProperty(PauseController.APP_NAME);
        doReturn(ffClient).when(featureFlags).getClient();
        doReturn(ldClient).when(featureFlags).getLDClient();
        doReturn(flagTracker).when(ldClient).getFlagTracker();
        doReturn(false).when(ldClient).isFlagKnown(any());
        doReturn(0).when(ffClient).getIntegerVariation(eq(PauseController.GLOBAL_PAUSE_INTERVAL_KEY), anyInt());
    }

    @Test
    void setApplicationContext() {
        ArgumentCaptor<FlagValueChangeListener> globalPauseListener = ArgumentCaptor.forClass(FlagValueChangeListener.class);
        ArgumentCaptor<FlagValueChangeListener> appPauseListener = ArgumentCaptor.forClass(FlagValueChangeListener.class);
        ArgumentCaptor<FlagValueChangeListener> globalIntervalListener = ArgumentCaptor.forClass(FlagValueChangeListener.class);
        ArgumentCaptor<FlagValueChangeListener> appIntervalListener = ArgumentCaptor.forClass(FlagValueChangeListener.class);

        doReturn(null).when(flagTracker).addFlagValueChangeListener(eq(PauseController.GLOBAL_PAUSE_KEY), any(), globalPauseListener.capture());
        doReturn(null).when(flagTracker).addFlagValueChangeListener(eq(PauseController.GLOBAL_PAUSE_INTERVAL_KEY), any(), globalIntervalListener.capture());
        doReturn(null).when(flagTracker).addFlagValueChangeListener(eq("Test.pause"), any(), appPauseListener.capture());
        doReturn(null).when(flagTracker).addFlagValueChangeListener(eq("Test.pause.interval"), any(), appIntervalListener.capture());

        pauseController.setApplicationContext(applicationContext);

        // sumbitted support case to LaunchDarkly.  There is no public way to create a FlagValueChangeEvent so code cannot be excercised.

        FlagValueChangeListener listener = globalPauseListener.getValue();
        FlagValueChangeEvent flagChangeEvent = new FlagValueChangeEvent(PauseController.GLOBAL_PAUSE_KEY, LDValue.of(true), LDValue.of(false));
        listener.onFlagValueChange(flagChangeEvent);

        flagChangeEvent = new FlagValueChangeEvent(PauseController.GLOBAL_PAUSE_KEY, LDValue.of(false), LDValue.of(true));
        listener.onFlagValueChange(flagChangeEvent);

        listener = globalIntervalListener.getValue();
        flagChangeEvent = new FlagValueChangeEvent(PauseController.GLOBAL_PAUSE_INTERVAL_KEY, LDValue.of(5000), LDValue.of(30000));
        listener.onFlagValueChange(flagChangeEvent);

        listener = appPauseListener.getValue();
        flagChangeEvent = new FlagValueChangeEvent("Test.pause", LDValue.of(true), LDValue.of(false));
        listener.onFlagValueChange(flagChangeEvent);

        flagChangeEvent = new FlagValueChangeEvent("Test.pause", LDValue.of(false), LDValue.of(true));
        listener.onFlagValueChange(flagChangeEvent);

        listener = appIntervalListener.getValue();
        flagChangeEvent = new FlagValueChangeEvent("Test.pause.interval", LDValue.of(5000), LDValue.of(30000));
        listener.onFlagValueChange(flagChangeEvent);
    }
}

