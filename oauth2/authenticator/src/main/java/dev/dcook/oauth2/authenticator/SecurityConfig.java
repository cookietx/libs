package dev.dcook.oauth2.authenticator;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String AUTH_TOKEN = "authtoken";
    private static final String AUTH_SUB = "authSub";
    private static final String USER_NAME = "username";
    private static final String INTERNAL_USER = "C4_INTERNAL";
    private static final String ROLE_PREFIX = "ROLE_";

    private static final String PERMITALL_PATTERN = "/management/**";

    @Value("${auth.request.pattern:/cumulus/v1/**}")
    private String authRequestPattern;

    @Bean
    public SecurityFilterChain securityFilterChains(HttpSecurity http, JwtAuthConverter jwtAuthConverter) throws Exception {

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(new AntPathRequestMatcher(PERMITALL_PATTERN)).permitAll());

        if (authRequestPattern.contains(",")) {
            String[] authRequestPatterns = authRequestPattern.split(", ");
            for (String requestPattern : authRequestPatterns) {
                http
                        .authorizeHttpRequests(authorize -> authorize
                                .requestMatchers(new AntPathRequestMatcher(requestPattern)).authenticated());
            }
        } else {
            http
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(new AntPathRequestMatcher(authRequestPattern)).authenticated());
        }

        return http
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().denyAll())
                .oauth2ResourceServer( oauth2->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(STATELESS))
                .build();
    }

    public static boolean isInternalUser() {
        return checkUserRole(INTERNAL_USER);
    }

    public static String getUserId() {
        return MDC.get(AUTH_SUB);
    }

    public static String getUserName() {
        return MDC.get(USER_NAME);
    }
    public static boolean checkUserRole(String roleName) {
        String prefixedRole = ROLE_PREFIX + roleName;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(prefixedRole));
    }

    public static String userToken() {
        String token = MDC.get(AUTH_TOKEN);
        return token != null ? token : "NoTokenAvailable";
    }

    public static boolean isUserTokenAvailable() {
        return MDC.get(AUTH_TOKEN) != null;
    }
}
