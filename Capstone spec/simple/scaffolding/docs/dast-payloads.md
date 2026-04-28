# DAST Payloads and Probes

## 1. Bulk-Transfer Abuse

We submitted 50 `WITHDRAWAL` requests as quickly as possible to the exact same account using concurrent automated requests.

**Result:**
The transactions successfully processed up to the maximum balance limit of the account due to `@Transactional` enforcing atomic read/writes. Once the balance dropped below zero, the application successfully and consistently returned a `422 INSUFFICIENT_FUNDS` error. 

## 2. Scheduled-Payment Payload Variations

### Malformed Amounts
Payload sent:
```json
{ "accountId":"acc_001", "type":"WITHDRAWAL", "amount": -1.00 }
```

**Result:**
The API returned a `400 Bad Request` with code `VALIDATION_FAILED` because the `NewTransactionRequest` DTO utilizes Bean Validation `@DecimalMin("0.01")`.

### XSS Attempt in Description
Payload sent:
```json
{ "accountId":"acc_001", "type":"DEPOSIT", "amount": 1.00, "description": "<script>alert(1)</script>" }
```

**Result:**
The API sanitized/escaped the input when saving and when reflecting it to the frontend React app.

## 3. Authorization Probes

**Probe:** Attempt to hit `/api/v1/admin/users` using a JWT bearing a `CUSTOMER` role.
**Result:**
The Spring Boot backend intercepted the request via both URL mapping in the Security configuration and the `@PreAuthorize("hasRole('ADMIN')")` decorator on the `UserController`. The API responded with a `403 Forbidden` response confirming successful gating.
