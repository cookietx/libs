package dev.dcook.oauth2.authenticator;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LogFilter extends OncePerRequestFilter {

    private static final String AUTH_TOKEN = "authtoken";
    private static final String MGT_URI = "/management/health".toUpperCase();

    @Value("${logapicallscontaining:none}")
    private String logApiCallsContaining;

    @Value("${logtruncateresponse:0}")
    private int logTruncateResponse;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        recordToken(request);

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long start = System.currentTimeMillis();
        filterChain.doFilter(requestWrapper, responseWrapper);
        long duration = System.currentTimeMillis() - start;
        String uri =request.getRequestURI();
        if(!(uri.toUpperCase().contains(MGT_URI)) && uri.toUpperCase().contains(logApiCallsContaining.toUpperCase())) {

                String requestBody = getStringValue(requestWrapper.getContentAsByteArray(), request.getCharacterEncoding());
                String responseBody = getStringValue(responseWrapper.getContentAsByteArray(), response.getCharacterEncoding());
                int reponseBodyLen = responseBody.length();
                String truncated = "";
                if (logTruncateResponse > 0 && reponseBodyLen > logTruncateResponse) {
                    truncated = "(TRUNCATE)";
                    responseBody =  responseBody.substring(0, logTruncateResponse) + "...";
                }

                String username = request.getRemoteUser();
                logger.info(String.format("User: %s\t %d\t %s\t %s IP:%s\t Dur: %d\t Request Len: %d\t Payload: %s\t Response Len: %d%s Body: %s",
                        (username != null ? username : SecurityConfig.getUserName()), response.getStatus(), request.getMethod(),
                        uri, request.getRemoteAddr(), duration, requestBody.length(), requestBody, reponseBodyLen, truncated, responseBody));
        }

        responseWrapper.copyBodyToResponse();
    }

    private String getStringValue(byte[] contentAsByteArray, String characterEncoding) {
        try {
            return new String(contentAsByteArray, 0, contentAsByteArray.length, (characterEncoding == null)?"UTF-8":characterEncoding);
        } catch (UnsupportedEncodingException e) {
            logger.error("Cannot convert input",e);
        }
        return "";
    }

    private void recordToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer"))
            MDC.put(AUTH_TOKEN, token.substring(7));
        else
            MDC.remove(AUTH_TOKEN);

    }
}
