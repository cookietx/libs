package com.capturerx.common.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to display in the log the message producer and consumer topics/groups and also any REST API used.
 * This happens only once at application startup.  The information is then available in the log searches
 * facilitating problem research.
 * 
 * Class is stand-alone, can be added to any Spring Boot application.  Input source is application.properties
 * from which property keys ending in path, uri or url indicate REST APIs used by the application.  
 * Properties starting with emr.api or externalapi are also included but suffix testing is preferred.
 * Property keys of spring.cloud.stream.bindings.<steam name>.[destination | group] are used to 
 * determine message producer and consumers
 */

@Configuration
public class CommunicationChannelInfo implements ApplicationContextAware {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    void setOverrideLogger(Logger logger) { // for unit testing
        if (logger != null)
            this.log = logger;
    }

    Properties loadApplicationProperties(Resource resource) throws IOException {
        return PropertiesLoaderUtils.loadProperties(resource);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        try {
            Map<String, String> groupMap = new HashMap<>();
            Map<String, String> destMap = new HashMap<>();
            List<String> apiList  = new ArrayList<>();
            String cacheHost  = null;

            Pattern bindingsPattern = Pattern.compile("(spring\\.cloud\\.stream\\.bindings.)(.*\\.)(.*)");
            Pattern endswithPattern = Pattern.compile("path|Uri|Url|uri|url$");
            Pattern beginswithPattern = Pattern.compile("^[emr\\.api|externalapi].*");

            Resource resource = new ClassPathResource("application.properties");
            Properties props = loadApplicationProperties(resource);
            // props contains only keys from the local properties files, but contains ALL properties, not just the
            // ones for the active profiles.  When retrieving values they must be
            // obtained from the Spring Boot Environment to consider override values from the property distributor
            // and only includes properties appropriate to the active profiles

            for(Object key : props.keySet()) {
                Matcher matcher = bindingsPattern.matcher(key.toString());
                if (matcher.find()) {
                    switch(matcher.group(3)) {
                        case "group":
                            groupMap.put(matcher.group(2), applicationContext.getEnvironment().getProperty(key.toString()));
                            break;
                        case "destination":
                            destMap.put(matcher.group(2), applicationContext.getEnvironment().getProperty(key.toString()));
                            break;
                        default:
                            break;
                    }
                }else {
                    matcher = endswithPattern.matcher(key.toString());
                    if (matcher.find()) {
                        String val = applicationContext.getEnvironment().getProperty(key.toString());
                        if (val != null && val.toLowerCase().startsWith("http")) {
                            apiList.add(val);
                        }
                    } else {
                        matcher = beginswithPattern.matcher(key.toString());
                        if (matcher.find()) {
                            String val = applicationContext.getEnvironment().getProperty(key.toString());
                            if (val != null && val.toLowerCase().startsWith("http")) {
                                apiList.add(val);
                            }
                        } else {
                            if (key.toString().endsWith("cache.host"))
                                cacheHost = applicationContext.getEnvironment().getProperty(key.toString());
                        }
                    }
                }
            }
            if (cacheHost != null) {
                log.info("App uses cache host: " + cacheHost);
            }
            if (!apiList.isEmpty()) {
                apiList.stream().forEach(s -> log.info("App uses REST api: " + s));
            }
            if (!groupMap.isEmpty()) {
                groupMap.entrySet().stream().forEach((Entry<String, String> es) -> {
                    String topic = destMap.remove(es.getKey());
                    log.info(String.format("App consumes message topic %s with group %s",
                            topic, es.getValue()));
                });
            }
            if (!destMap.isEmpty()) {
                destMap.values().stream().forEach(s -> log.info("App produces message topic " + s.toString()));
            }
        } catch (Exception e) {
            log.error("Failed listing info", e);
        }

    }
}
