# README #

# Authenticator library
The authenticator library is used for the authentication and authorization of incoming requests.
Authentication is performed using openid-connect to an appropriate authorization server.

The dependency to be in included in the pom.xml file is

    <dependency>
      <groupId>com.capturerx.oauth2</groupId>
      <artifactId>authenticator</artifactId>
      <version>3.17.1.0-SNAPSHOT</version>
    </dependency>

Include in the @ComponentScan: 

`"com.capturerx.oauth2.authenticator"`

To use this feature the following properties must be defined.

* auth.realm = capturerx
* auth.server = https://ciii.capturerx.com  

These identify the location of the authentication server and the realm defined in it.  These can then be used to 
build the URI that the will be used to authenticate and each request and build an access token that can be sent with the
request to resource server that will handle the request.

* spring.security.oauth2.resourceserver.jwt.issuer-uri = ${auth.server}/auth/realms/${auth.realm}
* spring.security.oauth2.resourceserver.jwt.jwk-set-uri = ${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/certs

The next property is optional as the default value will be the preferred_username.  This is the value that is used
when building the authorities list from the access token.

* jwt.auth.converter.principle-attribute = preferred_username

The next required property is used to identify the authorized URI(s).  The value is a CSV list of AntMathing patterns
allowing more than one More than one pattern to be authenticated.  Note the pattern of /management/** will 
set to *permitAll*.  Failure to set this property value correctly will cause 403 error for URI(s) not properly 
defined.

Finally the next optional property is used to enable the logging of requests after being processed.  The log entry will inclued
the username, status method, URI, ipaddr, request body, and response body. Only request for which the URI contains the 
value of the property will be logged.  No managment/health requests are logged.

* logapicallscontaining = apiservices

## Provides
By including the authenticator in a project:

1. Support for the use of the Spring Security @PreAuthorize and @PostAuthorize annotations on controller endpoints.
2. Allow definition of authenticated URI(s)
3. Enables CORS
4. Support for logging endpoint (REST) requests in the log
5. Records authenticate user for use by secured client library

## Code Example
After dependency is established and properties defined the following is an example of the annotation can be used 
to secure a URI in a controller

`@GetMapping
@PreAuthorize("hasRole(T(com.capturerx.cumulus.secureapp.RolesList).C4_CANREADREFERDOC.toString())")
public String hello() { return "hello"; }`

# Secure WebClient library 

There are two separate use cases for using a secured WebClient.  The first is when an authenticated
user credential must be passed through to another request.  The second is when there is no authenticated
user and the authorized client-id/client-password combination must be used to establish authentication.

In either case he dependency to be in included in the pom.xml file is:

    <dependency>
      <groupId>com.capturerx.oauth2</groupId>
      <artifactId>securewebclient</artifactId>
      <version>3.17.1.0-SNAPSHOT</version>
    </dependency>

Include in the @ComponentScan:

`"com.capturerx.oauth2.webclient"`


Where an instance of a WebClient is needed use the Bean of type CrxWebClientBuilder.  This bean can either
be Autowired or passed in with the constructor of the class where it is needed.  Example:

    @Autowired
    CrxWebClientBuilder crxWebClientBuilder;

## Case 1, using an authenticated users credential to pass on to another request
For an **authenticated user credential must be passed through** a standard WebClient can be obtained 
for use and the token for the
authenticated user can be added as the "Authorization" header of the request.  To facilitate the
creation of the authorized request the class `SecurityConfig` class of in the Authenticator library offers
the static method userToken to provide access to the value to be added to the standard WebClient as a header

For this use case no other library is required.

### Code example
The first example is one using a standard WebClient to make a synchronous GET request.

    Sring result = crxWebClientBuilder.createWebClientBuilder().build().get()
      .uri(RESOURCE_URI).headers(h -> h.setBearerAuth(SecurityConfig.userToken()))
      .retrieve()
      .bodyToMono(String.class)
      .block();


The second example is one using a standard WebClient to make a synchronous POST request.

    Person person = new Person("Joe Smith);
    Person result = crxWebClientBuilder.createWebClientBuilder().build().post()
        .uri(RESOURCE_URI).headers(h -> h.setBearerAuth(SecurityConfig.userToken()))
        .body(BodyInserters.fromValue(body))
        .retrieve()
        .bodyToMono(Person.class)
        .block();


NOTE the default buffer size used by Spring WebClient is 262144 bytes.  If the data being retrieved
is larger the size of the buffer can be increase by defining the exchange strategy as in the following example.

    DataPermissionsDTO permit = crxWebClientBuilder.createWebClientBuilder()
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1000 * 1024))
                    .build()).build().get()
            .uri(uriBuilder.toUriString())
            .headers(h -> h.setBearerAuth(SecurityConfig.userToken()))
            .headers(h -> h.set("Accept",  MediaType.APPLICATION_JSON_VALUE))
            .retrieve()
            .toEntity(DataPermissionsDTO.class)
            .block();


## Case 2 using predefined client credentials to make a secured request

When an authenticated user is not available or when **a request secured with a client credential** is required within
a non web application, a secured `WebClient` is available to make the request.

The secure client credentials is used applications that do not have user access tokens, to make requests
to secured resources applications.  The secured client will obtained the appropriate access token
from the authorization server to be submitted with the request.

To use this feature the following properties must be defined.

* auth.realm = capturerx
* auth.server = https://ciii.capturerx.com

These identify the location of the authentication server and the realm defined in it.  These can then be
used to build the URI that the will be used to authenticate each request and build an access token that
can be sent with the request to resource server that will handle the request. The properties for the client-id
and the client-secret are also required.

* spring.security.oauth2.client.provider.crx.token-uri = ${auth.server}/auth/realms/${auth.realm}/protocol/openid-connect/token
* spring.security.oauth2.client.registration.crx.client-id=cumulus4processworkers
* spring.security.oauth2.client.registration.crx.client-secret=9f8fe8d8-ec8c-418c-b3b8-605bd7402f13
* spring.security.oauth2.client.registration.crx.authorization-grant-type=client_credentials

Access to a secured WebClient.Builder is available through a static method  
crxWebClientBuilder.createSecureWebClientBuilder() method.`

### Code example

The first example is one using a secured WebClient to make a synchronous GET request.

        String result = crxWebClientBuilder.createSecureWebClientBuilder().build().get()
                .uri(RESOURCE_URI)
                .retrieve()
                .bodyToMono(String.class)
                .block();

The second example is one using a secured WebClient to make a synchronous POST request.

        Person person = new Person("Joe Smith);
        Person result = crxWebClientBuilder.createSecureWebClientBuilder().build().post()
                .uri(RESOURCE_URI)
                .body(BodyInserters.fromValue(person))
                .retrieve().bodyToMono(Person.class)
                .block();





