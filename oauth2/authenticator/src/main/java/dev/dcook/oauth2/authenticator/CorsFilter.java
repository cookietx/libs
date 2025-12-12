package dev.dcook.oauth2.authenticator;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;


import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements Filter {

    private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ASTERICK = "*";
    private static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String ALLOW_MAX_AGE = "Access-Control-Max-Age";
    private static final String ALLOWED_HTTP_METHODS = "POST, PUT, GET, OPTIONS, DELETE";
    private static final String ALLOWED_HEADERS = "Authorization, Content-Type,Cache-Control,If-Modified-Since,Pragma,Access-Control-Allow-Origin,Access-Control-Expose-Headers,Location";
    private static final String MAX_AGE = "3600";
    private static final String OPTIONS = "OPTIONS";
    private static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    private static final String LOCATION = "Location";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        final HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader(ALLOW_ORIGIN, ASTERICK);
        response.setHeader(ALLOW_METHODS, ALLOWED_HTTP_METHODS);
        response.setHeader(ALLOW_HEADERS, ALLOWED_HEADERS);
        response.setHeader(ALLOW_MAX_AGE, MAX_AGE);
        response.setHeader(ACCESS_CONTROL_EXPOSE_HEADERS, LOCATION);
        if (OPTIONS.equalsIgnoreCase(((HttpServletRequest) req).getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            chain.doFilter(req, res);
        }
    }

    @Override
    public void destroy() {
        // nothing to do here
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        // nothing to do here
    }
}
