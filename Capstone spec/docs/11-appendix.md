# 11 — Appendix

## Working with Copilot effectively

The rubric grades both effective use *and* critical review. The two are equally important.

### Patterns that work

- **Prime context with a comment block, then let it write.** Open the file, write a comment describing the method's contract (inputs, outputs, exceptions, invariants), and let Copilot fill the body. Edit until the assertions match what the comment promised.
- **Generate test cases from the spec.** Open the relevant section of [API Contract](./03-api-contract.md) in one pane, the test class in the other, and paste the rule as a comment. Copilot is good at translating "must return 422 with code INSUFFICIENT_FUNDS" into a `MockMvc.perform(...)` assertion.
- **Refactor with a chat prompt.** "Extract this anonymous error handler into a named class with the same behaviour" — Copilot Chat handles the refactor cleanly and you review the diff.

### Patterns that fail the rubric

- **Accepting boilerplate without reading it.** Especially in security code — Copilot has been trained on a lot of insecure examples (`new InMemoryUserDetailsManager(User.withDefaultPasswordEncoder()...)`). If you wouldn't put it in production, don't merge it.
- **Test-coverage theatre.** Copilot generates tests that mock the system under test and then assert on the mock. That's a 100%-coverage zero-information test. The rubric explicitly grades this.
- **Hallucinated APIs.** Spring's API surface is large and Copilot occasionally invents methods. If your IDE underlines red, Copilot was wrong — don't fight the IDE.

### What to be ready to answer in the demo

For every method with your name on the commit, expect: "Walk me through this." If you can't, you fail the AI-Assisted Development rubric line. Read your own commits before the demo.

## Common pitfalls

### Backend

- **Oracle dialect quirks.** `LIMIT` doesn't exist in Oracle 11–18; use `FETCH FIRST n ROWS ONLY`. The capstone doesn't need pagination, but if you add it, beware.
- **`@Transactional` on `private` methods does nothing.** Spring's transactional proxy can only intercept public methods called from outside the bean. Don't rely on annotations on private helpers.
- **`spring.jpa.hibernate.ddl-auto: update` in dev is convenient and wrong.** Use `validate`. The schema lives in `db/migration/`, not in entity annotations.
- **Money in `double` slips back in via Copilot suggestions.** Sweep for it before submitting: `grep -r "double " backend/src/main/java | grep -i "amount\|balance\|currency"`.
- **Forgetting to publish to Kafka after commit, not during.** If you publish inside the `@Transactional` and the DB transaction rolls back, you've emitted a phantom event. Test the rollback path.

### Frontend

- **`useEffect` infinite loops.** When fetching in `useEffect`, declare the dependency array carefully. Setting state inside an effect that depends on that state loops forever.
- **Treating route params as numbers.** They are always strings — use `Number(id)` only after a presence check.
- **`<a href="/accounts">` instead of `<Link to="/accounts">`.** Module 9 slides 11–12 cover this. The first one full-reloads.
- **Forgetting the `*` route.** Bad URLs render blank. Add `<Route path="*" element={<NotFoundPage />} />`.

### Security

- **CORS `allowedOrigins("*")` with `allowCredentials(true)`.** Browsers reject this combo, and Checkmarx will flag it. List explicit origins.
- **Returning 403 instead of 404 for non-owned resources.** Leaks the existence of the resource. The spec is clear: 404.
- **Returning the raw exception message in error responses.** `e.getMessage()` for a Hibernate exception leaks SQL fragments. Never echo it.

## Reference card — Spring Security

```java
// Resource server validation, with audience check
http
  .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
      jwt.jwtAuthenticationConverter(jwtAuthConverter)));

// audiences via custom validator if your boot version doesn't expose `audiences:`
@Bean
JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
                      @Value("${app.audience}") String audience) {
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        token -> token.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience"))
    ));
    return decoder;
}

// Method security
@PreAuthorize("hasRole('ADMIN')")
public List<UserDto> listAllUsers() { ... }
```

## Reference card — BFF Spring config

```yaml
# bff/src/main/resources/application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          mock-auth:
            client-id: spa-client
            client-secret: ${OAUTH_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: openid, profile, email
        provider:
          mock-auth:
            issuer-uri: ${AUTH_SERVER_URL:http://localhost:9000}
```

```java
// bff: WebClient with the OAuth2 filter
@Bean
public WebClient resourceServerWebClient(OAuth2AuthorizedClientManager mgr,
                                         @Value("${bank.resource-server.base-url}") String baseUrl) {
    var oauth = new ServletOAuth2AuthorizedClientExchangeFilterFunction(mgr);
    oauth.setDefaultClientRegistrationId("mock-auth");
    return WebClient.builder().baseUrl(baseUrl).filter(oauth).build();
}

// bff: a proxy controller — Spring attaches the bearer token automatically
@GetMapping("/api/v1/accounts")
public Mono<List<AccountDto>> listAccounts() {
    return webClient.get().uri("/api/v1/accounts")
        .retrieve()
        .bodyToFlux(AccountDto.class)
        .collectList();
}
```

## Reference card — react-router (BFF model — no auth wrappers)

```jsx
<Routes>
  <Route element={<AppLayout/>}>
    <Route path="/" element={<AccountsPage/>} />
    <Route path="/accounts/:accountId" element={<AccountDetailPage/>} />
    <Route path="/transactions/new" element={<NewTransactionPage/>} />
    <Route path="/admin/users" element={<AdminUsersPage/>} />
  </Route>
  <Route path="*" element={<NotFoundPage/>} />
</Routes>
```

There is no `<RequireAuth>` and no `<CallbackPage>`. Spring Security gates the API; the SPA's `apiClient` redirects to `/oauth2/authorization/mock-auth` on a 401.

## Reference card — Spring Kafka

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 10
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        linger.ms: 10
        compression.type: lz4
```

```java
@Service
public class TransactionEventPublisher {
    private final KafkaTemplate<String, TransactionEvent> kafka;

    public void publish(TransactionEvent event) {
        kafka.send("transactions.completed", event.accountId(), event)
             .whenComplete((result, err) -> { /* log */ });
    }
}
```

## Where the source material lives

If you want to re-read a topic, the slides and labs are at:

- **Java 17 features** — `2609-JavaSpring-April6/Week 1/1. Advanced Java 17 Features/`
- **Spring REST + DTO + ControllerAdvice** — `2609-JavaSpring-April6/Week 1/2. Spring Boot Architecture and REST API Design/`
- **OAuth2 / OIDC / Spring Security** — `2609-JavaSpring-April6/Week 2/3. OAuth2 - OIDC Foundations and Spring Security/`
- **External API consumption + WireMock** — `2609-JavaSpring-April6/Week 2/4. Secure External API Consumption/`
- **Banking API + validation + RBAC pattern** — `2609-JavaSpring-April6/Week 2/Lab 5 - Banking API Validation Security.md` ← this is the closest analog to the capstone backend; reread it.
- **Oracle modeling and SQL** — `2609-JavaSpring-April6/Week 3/5. Oracle Data Modeling and SQL Optimization/`
- **PL/SQL, indexing, partitioning** — `2609-JavaSpring-April6/Week 3/6. PL-SQL, Indexing and Partioning/`
- **Spring Data JPA, N+1, transactions** — `2609-JavaSpring-April6/Week 3/7. Spring Data/`
- **Kafka with Spring Boot** — `2609-JavaSpring-April6/Week 4 .../8. Kafka Integration with Spring Boot/`
- **React + React Router + SPA security** — `2609-JavaSpring-April6/Week 4 .../9. React/`

## External references (use sparingly)

- Spring Security OAuth2 Client (BFF) — https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html
- Spring Security Resource Server — https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html
- Spring Authorization Server — https://docs.spring.io/spring-authorization-server/reference/getting-started.html
- Spring `WebClient` with OAuth2 — https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorized-clients.html#oauth2Client-webclient-servlet
- Spring Kafka — https://docs.spring.io/spring-kafka/reference/index.html
- React Router — https://reactrouter.com/en/main
- OWASP ZAP — https://www.zaproxy.org/docs/
- BFF for SPAs (OAuth WG draft) — https://datatracker.ietf.org/doc/draft-ietf-oauth-browser-based-apps/
- Google Identity (OAuth 2.0 for Web) — https://developers.google.com/identity/openid-connect/openid-connect

That's the whole spec. Good luck.
