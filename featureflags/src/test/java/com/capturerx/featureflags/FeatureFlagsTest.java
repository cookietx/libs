package com.capturerx.featureflags;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.util.List;
import java.util.Timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagsTest {
    @Mock
    ApplicationContext ctx;

    @Mock
    ConfigurableEnvironment env;

    @Mock
    ConfigurableApplicationContext cctx;

    @Mock
    ApplicationReadyEvent event;

    @Mock
    Timer timer;

    @Mock
    FeatureFlags.ErrorMsgLoggerTask errorMsgLoggerTask;

    @Mock
    LDClientInterface ldClient;

    @Mock
    FeatureFlags.DataSourceListener dataSourceListener;

    @Mock
    DataSourceStatusProvider dataSourceStatusProvider;

    @Mock
    DataSourceStatusProvider.Status status;

    @Mock
    Logger mockLogger;

    @InjectMocks
    FeatureFlags ffService;// = new FeatureFlags(env);

    private static final String SDK_KEY = "sdk-6ee065c9-a4b7-4454-8c7a-4b1c3f9f0b7b";
    private static final String BAD_SDK_KEY = "sdk-6ee065c9-a4b7-4454-8c7a-4b1c3f9f0b7C";

    @BeforeEach
    void init() {
        ffService.setOverrideLogger(mockLogger);
    }

    private ArgumentCaptor<String> getLoggerCaptor() {
        // setup to catch log messages
        ArgumentCaptor<String> logMsgCapture = ArgumentCaptor.forClass(String.class);
        lenient().doNothing().when(this.mockLogger).info(logMsgCapture.capture());
        lenient().doNothing().when(this.mockLogger).warn(logMsgCapture.capture());
        lenient().doNothing().when(this.mockLogger).error(logMsgCapture.capture());
        lenient().doNothing().when(this.mockLogger).error(logMsgCapture.capture(), any(Throwable.class));
        return logMsgCapture;
    }

    @Test
    void testSetApplicationContext_failNotInitialized() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        lenient().when(env.getProperty(FeatureFlags.LD_CONFIG_PATHS)).thenReturn(null);
        lenient().when(env.getProperty(FeatureFlags.LD_SDK_KEY)).thenReturn(SDK_KEY);
        lenient().when(env.getProperty(FeatureFlags.APP_NAME)).thenReturn("TestApplication");
        doReturn(ldClient).when(ffServiceSpy).createLDClient(SDK_KEY);
        doReturn(false).when(ldClient).isInitialized();

        ffServiceSpy.setApplicationContext(ctx);

        verify(env, times(2)).getProperty(anyString());
        verify(ffServiceSpy, times(1)).createLDClient(SDK_KEY);
        verify(ldClient, times(1)).isInitialized();

        List<String> logMsgs = logMsgCapture.getAllValues();
        String expected = String.format("Closing application, initialize Launch Darkly communications with key %s", SDK_KEY);
        assertEquals(expected, logMsgs.get(0));
    }

    @Test
    void testSetApplicationContextt_successInitialized() throws IOException {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        lenient().when(env.getProperty(FeatureFlags.LD_CONFIG_PATHS)).thenReturn(null);
        lenient().when(env.getProperty(FeatureFlags.LD_SDK_KEY)).thenReturn(SDK_KEY);
        lenient().when(env.getProperty(FeatureFlags.APP_NAME)).thenReturn("TestApplication");
        doReturn(ldClient).when(ffServiceSpy).createLDClient(SDK_KEY);
        doReturn(true).when(ldClient).isInitialized();
        doReturn(dataSourceListener).when(ffServiceSpy).createdDataSourceListener();
        when(ldClient.getDataSourceStatusProvider()).thenReturn(dataSourceStatusProvider);
        doNothing().when(dataSourceStatusProvider).addStatusListener(dataSourceListener);

        ffServiceSpy.setApplicationContext(ctx);
        ffServiceSpy.closeClient();

        verify(env, times(3)).getProperty(anyString());
        verify(ffServiceSpy, times(1)).createLDClient(SDK_KEY);
        verify(ldClient, times(1)).isInitialized();
        verify(ldClient, times(1)).getDataSourceStatusProvider();
        verify(ffServiceSpy, times(1)).createdDataSourceListener();
        verify(dataSourceStatusProvider, times(1)).addStatusListener(dataSourceListener);

        List<String> logMsgs = logMsgCapture.getAllValues();
        String expected = String.format("SDK successfully initialized using key %s", SDK_KEY);
        assertEquals(expected, logMsgs.get(0));
    }

    @Test
    void testSetApplicationContextt_failNoKey() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        lenient().doNothing().when(ffServiceSpy).shutdown();
        lenient().when(env.getProperty(FeatureFlags.LD_CONFIG_PATHS)).thenReturn(null);
        lenient().when(env.getProperty(FeatureFlags.LD_SDK_KEY)).thenReturn(null);
        lenient().when(env.getProperty(FeatureFlags.APP_NAME)).thenReturn("TestApplication");

        ffServiceSpy.setApplicationContext(ctx);
        ffServiceSpy.closeClient();

        verify(env, times(2)).getProperty(anyString());
        verify(ffServiceSpy, times(1)).shutdown();

        List<String> logMsgs = logMsgCapture.getAllValues();
        String expected = String.format("Closing application, no key or configuration found for Launch Darkly");
        assertEquals(expected, logMsgs.get(0));
    }

    @Test
    void testSetApplicationContext_successFileConfigInitialized() throws IOException {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        lenient().when(env.getProperty(FeatureFlags.LD_CONFIG_PATHS)).thenReturn("ldconfig.yaml");
        lenient().when(env.getProperty(FeatureFlags.LD_SDK_KEY)).thenReturn(SDK_KEY);
        lenient().when(env.getProperty(FeatureFlags.APP_NAME)).thenReturn("TestApplication");
        doReturn(ldClient).when(ffServiceSpy).createLDClient(FeatureFlags.USING_LOCAL_CONFIG);
        doReturn(true).when(ldClient).isInitialized();
        doReturn(dataSourceListener).when(ffServiceSpy).createdDataSourceListener();
        when(ldClient.getDataSourceStatusProvider()).thenReturn(dataSourceStatusProvider);
        doNothing().when(dataSourceStatusProvider).addStatusListener(dataSourceListener);

        ffServiceSpy.setApplicationContext(ctx);
        ffServiceSpy.closeClient();

        verify(env, times(0)).getProperty(FeatureFlags.LD_SDK_KEY);
        verify(env, times(1)).getProperty(FeatureFlags.LD_CONFIG_PATHS);
        verify(ffServiceSpy, times(1)).createLDClient(FeatureFlags.USING_LOCAL_CONFIG);
        verify(ldClient, times(1)).isInitialized();
        verify(ldClient, times(1)).getDataSourceStatusProvider();
        verify(ffServiceSpy, times(1)).createdDataSourceListener();
        verify(dataSourceStatusProvider, times(1)).addStatusListener(dataSourceListener);

        List<String> logMsgs = logMsgCapture.getAllValues();
        String expected = String.format("%s %s", FeatureFlags.USING_LOCAL_CONFIG, "ldconfig.yaml");
        assertEquals(expected, logMsgs.get(0));
    }

    @Test
    void testStdLDClientCreation() throws IOException {
        FeatureFlags ffServiceSpy = Mockito.spy(ffService);
        LDClient client = (LDClient) ffServiceSpy.createLDClient(SDK_KEY);
        assertTrue(client.isInitialized());
        client.close();
    }

    @Test
    void testFileConfigLDClientCreation() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();
        FeatureFlags ffServiceSpy = Mockito.spy(ffService);
        ffServiceSpy.setLDConfigFilesPaths("src/test/resources/ldconfig.yaml");
        LDClient client = (LDClient) ffServiceSpy.createLDClient(FeatureFlags.USING_LOCAL_CONFIG);
        assertTrue(client.isInitialized());
    }

    @Test
    void testLoggingTimerTask_stateValid() {
        DataSourceStatusProvider.State stateValid = DataSourceStatusProvider.State.VALID;

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(ldClient).when(ffServiceSpy).getLDClient();
        when(ldClient.getDataSourceStatusProvider()).thenReturn(dataSourceStatusProvider);
        when(dataSourceStatusProvider.getStatus()).thenReturn(status);
        when(status.getState()).thenReturn(stateValid);
//        doNothing().when(ffServiceSpy).cancelLoggingTask();

        ffServiceSpy.createErrorMsgLoggerTask().run();

        verify(ffServiceSpy, times(1)).getLDClient();
        verify(ldClient, times(1)).getDataSourceStatusProvider();
        verify(dataSourceStatusProvider, times(1)).getStatus();
        verify(status, times(1)).getState();
        verify(ffServiceSpy, times(1)).cancelLoggingTask();
    }
    @Test
    void testLoggingTimerTask_stateInterrupted() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();
        DataSourceStatusProvider.State stateInterrupted = DataSourceStatusProvider.State.INTERRUPTED;

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(ldClient).when(ffServiceSpy).getLDClient();
        when(ldClient.getDataSourceStatusProvider()).thenReturn(dataSourceStatusProvider);
        when(dataSourceStatusProvider.getStatus()).thenReturn(status);
        when(status.getState()).thenReturn(stateInterrupted);

        ffServiceSpy.createErrorMsgLoggerTask().run();

        verify(ffServiceSpy, times(1)).getLDClient();
        verify(ldClient, times(1)).getDataSourceStatusProvider();
        verify(dataSourceStatusProvider, times(1)).getStatus();
        verify(status, times(1)).getState();
        verify(ffServiceSpy, times(0)).cancelLoggingTask();

        List<String> logMsgs = logMsgCapture.getAllValues();
        String expected = String.format(FeatureFlags.STATE_ERROR_MSG, stateInterrupted);
        assertEquals(expected, logMsgs.get(0));
    }

    @Test
    void testDataSourceListener_interrupted() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();
        DataSourceStatusProvider.State stateInterrupted = DataSourceStatusProvider.State.INTERRUPTED;

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(timer).when(ffServiceSpy).createLoggingTimer();
        doReturn(errorMsgLoggerTask).when(ffServiceSpy).createErrorMsgLoggerTask();
        doNothing().when(timer).schedule(errorMsgLoggerTask, FeatureFlags.INIT_DELAY, FeatureFlags.RECUR_DELAY);
        when(status.getState()).thenReturn(stateInterrupted);

        ffServiceSpy.createdDataSourceListener().dataSourceStatusChanged(status);

        verify(ffServiceSpy, times(1)).createErrorMsgLoggerTask();
        verify(timer, times(1)).schedule(errorMsgLoggerTask, FeatureFlags.INIT_DELAY, FeatureFlags.RECUR_DELAY);

        List<String> logMsgs = logMsgCapture.getAllValues();
        String expected = String.format(FeatureFlags.STATE_ERROR_MSG, stateInterrupted);
        assertEquals(expected, logMsgs.get(0));
    }


    @Mock
    EvaluationDetail<String> stringDetail;

    @Mock
    EvaluationDetail<Integer> intDetail;

    @Mock
    EvaluationDetail<Double> doubleDetail;

    @Mock
    EvaluationDetail<Boolean> booleanDetail;


    static final String FLAG_KEY = "flag-key";
    static final String USER_NAME = "username";
    static final String LOGIN = "someuser@wherever.com";
    static final String DEF_STR = "dummy value";
    static final String CACHED_STR = "cached value";
    static final Integer DEF_INT = 0;
    static final Integer CACHED_INT = 1;
    static final Double DEF_DBL = 0.00;
    static final Double CACHED_DBL = 1.00;
    static final Boolean DEF_BOOL = Boolean.FALSE;
    static final Boolean CACHED_BOOL = Boolean.TRUE;

    @Test
    void testGetStringVariation_error() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(ldClient).when(ffServiceSpy).getLDClient();
        when(ldClient.stringVariationDetail(matches(FLAG_KEY), any(LDContext.class), matches(DEF_STR))).thenReturn(stringDetail);
        when(stringDetail.getValue()).thenReturn(DEF_STR);
        String detailStr = String.format("{%s,%d,%s}", DEF_STR, 0, EvaluationReason.error(EvaluationReason.ErrorKind.EXCEPTION));
        when(stringDetail.toString()).thenReturn(detailStr);

        String result = ffServiceSpy.getClient().getStringVariation(FLAG_KEY, DEF_STR);

        assertEquals(DEF_STR, result);
        List<String> logMsgs = logMsgCapture.getAllValues();
        assertTrue(logMsgs.get(0).contains(detailStr));
        assertTrue(logMsgs.get(0).contains(FeatureFlags.DEFAULT_USER_KEY));
    }

    @Test
    void testGetStringVariation_loginUserAndDetailLogging_success() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        MDC.put(USER_NAME, LOGIN);

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(ldClient).when(ffServiceSpy).getLDClient();
        when(ldClient.boolVariation(matches(FeatureFlags.DETAIL_LOGGING_KEY), any(LDContext.class), anyBoolean())).thenReturn(true);

        when(ldClient.stringVariationDetail(matches(FLAG_KEY), any(LDContext.class), matches(DEF_STR))).thenReturn(stringDetail);
        when(stringDetail.getValue()).thenReturn(CACHED_STR);
        String detailStr = String.format("{%s,%d,%s}", CACHED_STR, 0, EvaluationReason.fallthrough());
        when(stringDetail.toString()).thenReturn(detailStr);

        String result = ffServiceSpy.getClient().getStringVariation(FLAG_KEY, DEF_STR);
        assertEquals(CACHED_STR, result);
        List<String> logMsgs = logMsgCapture.getAllValues();
        assertTrue(logMsgs.get(0).contains(detailStr));
        assertTrue(logMsgs.get(0).contains(LOGIN));

        MDC.remove(USER_NAME);
    }

    @Test
    void testGetIntegerVariation_error() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(ldClient).when(ffServiceSpy).getLDClient();
        when(ldClient.intVariationDetail(matches(FLAG_KEY), any(LDContext.class), anyInt())).thenReturn(intDetail);
        when(intDetail.getValue()).thenReturn(DEF_INT);
        String detailStr = String.format("{%d,%d,%s}", DEF_INT, 0, EvaluationReason.error(EvaluationReason.ErrorKind.EXCEPTION));
        when(intDetail.toString()).thenReturn(detailStr);

        Integer result = ffServiceSpy.getClient().getIntegerVariation(FLAG_KEY, DEF_INT);

        assertEquals(DEF_INT, result);
        List<String> logMsgs = logMsgCapture.getAllValues();
        assertTrue(logMsgs.get(0).contains(detailStr));
        assertTrue(logMsgs.get(0).contains(FeatureFlags.DEFAULT_USER_KEY));
    }


    @Test
    void testGetIntVariation_success() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(ldClient).when(ffServiceSpy).getLDClient();
        when(ldClient.intVariationDetail(matches(FLAG_KEY), any(LDContext.class), anyInt())).thenReturn(intDetail);
        when(intDetail.getValue()).thenReturn(CACHED_INT);
        String detailStr = String.format("{%d,%d,%s}", CACHED_INT, 0, EvaluationReason.fallthrough());
        when(intDetail.toString()).thenReturn(detailStr);

        Integer result = ffServiceSpy.getClient().getIntegerVariation(FLAG_KEY, DEF_INT);

        assertEquals(CACHED_INT, result);
        List<String> logMsgs = logMsgCapture.getAllValues();
        assertEquals(0, logMsgs.size());

    }

    @Test
    void testGetDoubleVariation_error() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(ldClient).when(ffServiceSpy).getLDClient();
        when(ldClient.doubleVariationDetail(matches(FLAG_KEY), any(LDContext.class), anyDouble())).thenReturn(doubleDetail);
        when(doubleDetail.getValue()).thenReturn(DEF_DBL);
        String detailStr = String.format("{%f,%d,%s}", DEF_DBL, 0, EvaluationReason.error(EvaluationReason.ErrorKind.EXCEPTION));
        when(doubleDetail.toString()).thenReturn(detailStr);

        Double result = ffServiceSpy.getClient().getDoubleVariation(FLAG_KEY, DEF_DBL);

        assertEquals(DEF_DBL, result);
        List<String> logMsgs = logMsgCapture.getAllValues();
        assertTrue(logMsgs.get(0).contains(detailStr));
        assertTrue(logMsgs.get(0).contains(FeatureFlags.DEFAULT_USER_KEY));
    }


    @Test
    void testGetDoubleVariation_success() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(ldClient).when(ffServiceSpy).getLDClient();
        when(ldClient.doubleVariationDetail(matches(FLAG_KEY), any(LDContext.class), anyDouble())).thenReturn(doubleDetail);
        when(doubleDetail.getValue()).thenReturn(CACHED_DBL);
        String detailStr = String.format("{%f,%d,%s}", CACHED_DBL, 0, EvaluationReason.fallthrough());
        when(doubleDetail.toString()).thenReturn(detailStr);

        Double result = ffServiceSpy.getClient().getDoubleVariation(FLAG_KEY, DEF_DBL);

        assertEquals(CACHED_DBL, result);
        List<String> logMsgs = logMsgCapture.getAllValues();
        assertEquals(0, logMsgs.size());

    }

    @Test
    void testGetBooleanVariation_error() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(ldClient).when(ffServiceSpy).getLDClient();
        when(ldClient.boolVariationDetail(matches(FLAG_KEY), any(LDContext.class), anyBoolean())).thenReturn(booleanDetail);
        when(booleanDetail.getValue()).thenReturn(DEF_BOOL);
        String detailStr = String.format("{%s,%d,%s}", DEF_BOOL, 0, EvaluationReason.error(EvaluationReason.ErrorKind.EXCEPTION));
        when(booleanDetail.toString()).thenReturn(detailStr);

        Boolean result = ffServiceSpy.getClient().getBooleanVariation(FLAG_KEY, DEF_BOOL);

        assertEquals(DEF_BOOL, result);
        List<String> logMsgs = logMsgCapture.getAllValues();
        assertTrue(logMsgs.get(0).contains(detailStr));
        assertTrue(logMsgs.get(0).contains(FeatureFlags.DEFAULT_USER_KEY));
    }


    @Test
    void testGetBooleanVariation_success() {
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        FeatureFlags ffServiceSpy = Mockito.spy(ffService);

        doReturn(ldClient).when(ffServiceSpy).getLDClient();
        when(ldClient.boolVariationDetail(matches(FLAG_KEY), any(LDContext.class), anyBoolean())).thenReturn(booleanDetail);
        when(booleanDetail.getValue()).thenReturn(CACHED_BOOL);
        String detailStr = String.format("{%s,%d,%s}", CACHED_BOOL, 0, EvaluationReason.fallthrough());
        when(booleanDetail.toString()).thenReturn(detailStr);

        Boolean result = ffServiceSpy.getClient().getBooleanVariation(FLAG_KEY, DEF_BOOL);

        assertEquals(CACHED_BOOL, result);
        List<String> logMsgs = logMsgCapture.getAllValues();
        assertEquals(0, logMsgs.size());

    }

}