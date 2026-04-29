# SAST Findings

This document covers the Static Application Security Testing findings and their respective triage. 

| Finding ID | Severity | Category | File | Decision | Rationale | Rescan Result |
|---|---|---|---|---|---|---|
| CX-0001 | High | Hardcoded Credentials | `application-dev.yml` | Fix | The DB password was hardcoded in properties. Replaced with `${ORACLE_PASSWORD}`. | Closed |
| CX-0002 | Medium | Information Exposure | `GlobalExceptionHandler.java` | Fix | Raw exceptions were returned leading to full stack traces. Fixed by using a generic error envelope (RFC 7807) and logging internally. | Closed |
| CX-0003 | Low | Permissive CORS | `CorsConfig.java` | Fix | CORS was allowing `*` origins. Fixed to exclusively allow `http://localhost:5173`. | Closed |
