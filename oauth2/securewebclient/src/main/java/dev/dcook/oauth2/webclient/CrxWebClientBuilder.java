package dev.dcook.oauth2.webclient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Component
@Slf4j
public class CrxWebClientBuilder implements ApplicationContextAware {

    private ServerOAuth2AuthorizedClientExchangeFilterFunction oauth;
    @Value("${crx.webclient.maxConnections:3}")
    private int maxConnections;
    @Value("${crx.webclient.maxIdleTimeSec:10}")
    private int maxIdleTimeSec;
    @Value("${crx.webclient.maxLifeTimeSec:30}")
    private int maxLifeTimeSec;
    @Value("${crx.webclient.pendingAcquireTimeoutSec:60}")
    private int pendingAcquireTimeoutSec;
    @Value("${crx.webclient.evictInBackgroundSec:60}")
    private int evictInBackgroundSec;

    ApplicationContext applicationContext;
    private ReactorClientHttpConnector reactorClientHttpConnector;

    ServerOAuth2AuthorizedClientExchangeFilterFunction getServerOAuth2AuthorizedClientExchangeFilterFunction() {
        if (oauth == null) {
            WebClientConfig webClientConfig = (WebClientConfig) applicationContext.getBean("webClientConfig");
            oauth = webClientConfig.crxSecuredWebClientFilter();
        }
        return oauth;
    }

    private void validateConfig() {
        maxConnections = maxConnections < 3  ?  3 : Math.min(maxConnections, 5000);
        maxIdleTimeSec = maxIdleTimeSec < 1  ?  1 : Math.min(maxIdleTimeSec, 30);
        maxLifeTimeSec = maxLifeTimeSec < 3  ?  3 : Math.min(maxLifeTimeSec, 60);
        pendingAcquireTimeoutSec = pendingAcquireTimeoutSec < 60 ? 60 : Math.min(pendingAcquireTimeoutSec, 120);
        evictInBackgroundSec = evictInBackgroundSec < 60 ? 60 : Math.min(evictInBackgroundSec, 120);
        log.info("crx.webclient. values: maxConnections={}, maxIdleTimeSec={}, maxLifeTimeSec={}, pendingAcquireTimeoutSec{}" +
                ", evictInBackgroundSec{}", maxConnections, maxIdleTimeSec, maxLifeTimeSec, pendingAcquireTimeoutSec, evictInBackgroundSec);
    }

    synchronized ReactorClientHttpConnector getReactorClientHttpConnector() {
        if (reactorClientHttpConnector == null) {
            validateConfig();
            ConnectionProvider provider = ConnectionProvider.builder("crx")
                    .maxConnections(maxConnections)
                    .maxIdleTime(Duration.ofSeconds(maxIdleTimeSec))
                    .maxLifeTime(Duration.ofSeconds(maxLifeTimeSec))
                    .pendingAcquireTimeout(Duration.ofSeconds(pendingAcquireTimeoutSec))
                    .evictInBackground(Duration.ofSeconds(evictInBackgroundSec)).build();
            reactorClientHttpConnector = new ReactorClientHttpConnector(HttpClient.create(provider));
        }
        return reactorClientHttpConnector;
    }

    WebClient.Builder getBuilder() {
        return WebClient.builder();
    }

    public WebClient.Builder createWebClientBuilder() {
        return getBuilder().clientConnector(getReactorClientHttpConnector());
    }

    public WebClient.Builder createSecureWebClientBuilder() {
        return createWebClientBuilder().filter(getServerOAuth2AuthorizedClientExchangeFilterFunction());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
       this.applicationContext = applicationContext;
    }
}
