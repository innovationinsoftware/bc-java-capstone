# Architecture

## System diagram

`mermaid
graph TD;
    SPA[React 18 SPA] -->|HttpOnly JSESSIONID Cookie| API[Spring Boot 3 BFF];
    API -->|Authorization Code Flow + Client Secret| Google[Google OAuth2];
    Google -->|ID Token + Access Token| API;
    API -->|Validation| GoogleJWKS[Google JWKS];
    API -->|JDBC| DB[(Oracle Database)];
    API -->|Produce Events| Kafka[Kafka Broker];
    API -->|Downstream REST| Wiremock[WireMock (Payment Processor)];
`

## Key Technologies
*   **Frontend:** React 18, Vite, Axios
*   **Backend:** Java 17, Spring Boot 3.2, Spring Security (OAuth2 Client & BFF Pattern), Spring Data JPA
*   **Database:** Oracle Database (via Docker)
*   **Events:** Apache Kafka (via Docker)
*   **Mocks:** WireMock for external payment APIs
