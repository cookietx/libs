package com.capturerx.oauth2.authenticator;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
This custom Converter is required because the roles that are are provided by keycloak, at least
the CaptureRX flavor, is providing the C4_ roles under the realm_access node instead of the
resource_access node where Spring Security v6 OAuth2 support would like them to be.
 */

@Component
@Slf4j
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Value("${jwt.auth.converter.principle-attribute:preferred_username}")
    private String principleAttribute;

    private static final String USER_NAME = "username";
    private static final String AUTH_SUB = "authSub";

    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter =
            new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                jwtGrantedAuthoritiesConverter.convert(jwt).stream(),
                extractResourceRoles(jwt).stream()
        ).collect(Collectors.toSet());

        String sub = jwt.getClaim(JwtClaimNames.SUB);
        if (sub != null)
            MDC.put(AUTH_SUB, sub);
        else
            MDC.remove(AUTH_SUB);

        String username = getPrincipleClaimName(jwt);

        // if coming from a login user, make the login id available for later use.
        if (username != null && username.contains("@"))
            MDC.put(USER_NAME, username);
        else
            MDC.remove(USER_NAME);

        return new JwtAuthenticationToken(
                jwt,
                authorities,
                username
        );
    }

    private String getPrincipleClaimName(Jwt jwt) {
        String claimName = JwtClaimNames.SUB;
        if (principleAttribute != null) {
            claimName = principleAttribute;
        }
        return jwt.getClaim(claimName);
    }

    private Collection<? extends GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return Set.of();
        }

        Collection<String> resourceRoles = (Collection<String>) realmAccess.get("roles");
        return resourceRoles
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toSet());
    }
}
