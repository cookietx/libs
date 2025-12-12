package dev.dcook.oauth2.resttemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SecuredUserHelper {

    private static final String AUTH_TOKEN = "authtoken";

    private ObjectMapper mapper = new ObjectMapper();

    public HttpEntity createEntityForSecuredUser(Object body) {

        String token = MDC.get(AUTH_TOKEN);
        if (token == null) {
            log.error("Cannot create entity object, no user credentials found.");
            return null;
        }

        String bodyAsJson = null;
        if (body != null) {
            try {
                bodyAsJson = mapper.writeValueAsString(body);
            } catch (JsonProcessingException e) {
                log.error("Cannot create JSON for body", e);
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity <> (bodyAsJson, headers);
    }
}
