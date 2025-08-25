package com.capturerx.common.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunicationChannelInfoTest {

    @Mock
    Logger mockLogger;

    @Mock
    ConfigurableEnvironment env;

    @Mock
    ConfigurableApplicationContext ctx;

    @Mock
    PropertiesLoaderUtils loaderUtils;

    @InjectMocks
    CommunicationChannelInfo communicationChannelInfo;

    private ArgumentCaptor<String> getLoggerCaptor() {
        // setup to catch log messages
        ArgumentCaptor<String> logMsgCapture = ArgumentCaptor.forClass(String.class);
        lenient().doNothing().when(this.mockLogger).info(logMsgCapture.capture());
        lenient().doNothing().when(this.mockLogger).warn(logMsgCapture.capture());
        lenient().doNothing().when(this.mockLogger).error(logMsgCapture.capture());
        lenient().doNothing().when(this.mockLogger).error(logMsgCapture.capture(), any(Throwable.class));
        return logMsgCapture;
    }
    @BeforeEach
    void setUp() {

    }

    @Test
    void testonApplicationEvent() throws IOException {
        CommunicationChannelInfo communicationChannelInfoSpy = Mockito.spy(CommunicationChannelInfo.class);

        communicationChannelInfoSpy.setOverrideLogger(mockLogger);
        ArgumentCaptor<String> logMsgCapture = getLoggerCaptor();

        Properties p = new Properties();

        // represent properties that are not in active profile
        p.put("spring.cloud.stream.bindings.dummy.destination", "c4_ci_dummy");
        p.put("dummy-server-url", "http://localhost:4040/dummy");
        p.put("apidummy.path", "http://localhost:8085/apidummy/cumulus/v1/");

        p.put("spring.cloud.stream.bindings.orchestratorToDistributor.destination", "c4_ci_distworker");
        p.put("spring.cloud.stream.bindings.orchestratorInputChannel.destination", "c4_ci_orworker_request");
        p.put("spring.cloud.stream.bindings.orchestratorInputChannel.group", "c4_ci_orworker_request");
        p.put("keycloak.auth-server-url", "http://localhost:4040/auth");
        p.put("apiservices.path", "http://localhost:8085/apiservice/cumulus/v1/");
        p.put("spring.datasource.url", "jdbc:postgresql://host.docker.internal:5432/cumulus4virtualinventorydb?ApplicationName=VirtualInventoryAPI");
        p.put("externalapi.medispanapi", "http://localhost:8097/drugs/cumulus/v1/medispan");

        doReturn(p).when(communicationChannelInfoSpy).loadApplicationProperties(any(Resource.class));

        when(ctx.getEnvironment()).thenReturn(env);

        // responses for properties not in active profile
        doReturn(null).when(env).getProperty("spring.cloud.stream.bindings.dummy.destination");
        doReturn(null).when(env).getProperty("dummy-server-url");
        doReturn(null).when(env).getProperty("apidummy.path");

        // responses for properties in active profile
        doReturn("c4_ci_distworker").when(env).getProperty("spring.cloud.stream.bindings.orchestratorToDistributor.destination");
        doReturn("c4_ci_orworker_request").when(env).getProperty("spring.cloud.stream.bindings.orchestratorInputChannel.destination");
        doReturn("c4_ci_orworker_request").when(env).getProperty("spring.cloud.stream.bindings.orchestratorInputChannel.group");
        doReturn("http://localhost:4040/auth").when(env).getProperty("keycloak.auth-server-url");
        doReturn("http://localhost:8085/apiservice/cumulus/v1/").when(env).getProperty("apiservices.path");
        doReturn("http://localhost:8097/drugs/cumulus/v1/medispan").when(env).getProperty("externalapi.medispanapi");
        doReturn("jdbc:postgresql://host.docker.internal:5432/cumulus4virtualinventorydb?ApplicationName=VirtualInventoryAPI").when(env).getProperty("spring.datasource.url");

        communicationChannelInfoSpy.setApplicationContext(ctx);

        List<String> logMsgs = logMsgCapture.getAllValues();
        assertEquals(6, logMsgs.size());

    }

}