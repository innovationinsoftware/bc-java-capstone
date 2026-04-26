# 02 — Domain Model & Database

## Domain in plain English

- A **bank user** is the person who logs in. They are identified by their Google subject (`sub`) claim. We track them locally so we can attach a role and a list of accounts.
- An **account** belongs to exactly one user, has a type (`CHECKING` or `SAVINGS`), a currency (USD only for the capstone), and a current balance. A user may own multiple accounts.
- A **transaction** records a single movement of money against an account. It has a type (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER_OUT`, `TRANSFER_IN`), an amount, an optional counterparty account, a status (`PENDING`, `COMPLETED`, `FAILED`), and a created timestamp.

A `TRANSFER` between two of the user's own accounts produces **two** transaction rows: one `TRANSFER_OUT` on the source, one `TRANSFER_IN` on the destination. They share a `transfer_group_id` so they can be correlated. A `TRANSFER` to an external account produces one `TRANSFER_OUT` row plus a Payment Processor call (see [Security](./04-security.md)).

## Oracle schema

These DDL statements are the authoritative schema. The scaffold ships with them in `backend/src/main/resources/db/migration/V1__initial_schema.sql`. Do not rename columns; controllers and services depend on the names.

```sql
CREATE TABLE BANK_USERS (
    USER_ID         VARCHAR2(64)   NOT NULL,
    SUBJECT         VARCHAR2(255)  NOT NULL,
    EMAIL           VARCHAR2(255)  NOT NULL,
    DISPLAY_NAME    VARCHAR2(255),
    ROLE            VARCHAR2(16)   NOT NULL,
    CREATED_AT      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_BANK_USERS PRIMARY KEY (USER_ID),
    CONSTRAINT UQ_BANK_USERS_SUBJECT UNIQUE (SUBJECT),
    CONSTRAINT CK_BANK_USERS_ROLE CHECK (ROLE IN ('CUSTOMER','ADMIN'))
);

CREATE TABLE ACCOUNTS (
    ACCOUNT_ID      VARCHAR2(64)   NOT NULL,
    OWNER_ID        VARCHAR2(64)   NOT NULL,
    ACCOUNT_TYPE    VARCHAR2(16)   NOT NULL,
    CURRENCY        VARCHAR2(3)    DEFAULT 'USD' NOT NULL,
    BALANCE         NUMBER(19, 4)  DEFAULT 0     NOT NULL,
    CREATED_AT      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_ACCOUNTS PRIMARY KEY (ACCOUNT_ID),
    CONSTRAINT FK_ACCOUNTS_OWNER FOREIGN KEY (OWNER_ID) REFERENCES BANK_USERS(USER_ID),
    CONSTRAINT CK_ACCOUNTS_TYPE CHECK (ACCOUNT_TYPE IN ('CHECKING','SAVINGS')),
    CONSTRAINT CK_ACCOUNTS_BALANCE CHECK (BALANCE >= 0)
);

CREATE INDEX IX_ACCOUNTS_OWNER ON ACCOUNTS (OWNER_ID);

CREATE TABLE TRANSACTIONS (
    TRANSACTION_ID      VARCHAR2(64)   NOT NULL,
    ACCOUNT_ID          VARCHAR2(64)   NOT NULL,
    TYPE                VARCHAR2(16)   NOT NULL,
    AMOUNT              NUMBER(19, 4)  NOT NULL,
    STATUS              VARCHAR2(16)   NOT NULL,
    COUNTERPARTY        VARCHAR2(64),
    TRANSFER_GROUP_ID   VARCHAR2(64),
    DESCRIPTION         VARCHAR2(255),
    CREATED_AT          TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_TRANSACTIONS PRIMARY KEY (TRANSACTION_ID),
    CONSTRAINT FK_TRANSACTIONS_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES ACCOUNTS(ACCOUNT_ID),
    CONSTRAINT CK_TRANSACTIONS_TYPE CHECK (TYPE IN ('DEPOSIT','WITHDRAWAL','TRANSFER_OUT','TRANSFER_IN')),
    CONSTRAINT CK_TRANSACTIONS_STATUS CHECK (STATUS IN ('PENDING','COMPLETED','FAILED')),
    CONSTRAINT CK_TRANSACTIONS_AMOUNT CHECK (AMOUNT > 0)
);

CREATE INDEX IX_TRANSACTIONS_ACCOUNT_CREATED
    ON TRANSACTIONS (ACCOUNT_ID, CREATED_AT DESC);

CREATE INDEX IX_TRANSACTIONS_GROUP
    ON TRANSACTIONS (TRANSFER_GROUP_ID);
```

### Notes on the schema

- **`AMOUNT` and `BALANCE` are `NUMBER(19, 4)`**, not `NUMBER` or floating point. Money in floats is a classic SAST/DAST finding — Checkmarx will flag it. Map it to `BigDecimal` in Java, never `double`.
- **`USER_ID` / `ACCOUNT_ID` / `TRANSACTION_ID` are application-generated `VARCHAR2`** (e.g., UUIDs prefixed with `usr_`, `acc_`, `txn_`). This avoids leaking sequential IDs in URLs and matches what the rubric expects under "endpoint hardening."
- **`SUBJECT` is unique** — that's how you tie a Google identity to a local user record. On first login, your code creates the row.
- The `CHECK` constraints are a safety net. Your service layer must validate **before** hitting the DB; if you rely on Oracle to enforce business rules you'll get ugly DataIntegrityViolationExceptions in the controller layer.

### Sample data

The scaffold ships with `V2__seed_data.sql`:

```sql
INSERT INTO BANK_USERS (USER_ID, SUBJECT, EMAIL, DISPLAY_NAME, ROLE) VALUES
    ('usr_demo_admin', 'google-sub-admin-placeholder', 'admin@example.com', 'Demo Admin', 'ADMIN');

-- Demo customer accounts are created on first login from a real Google identity.
-- Run V3__demo_accounts.sql AFTER you've logged in once to attach accounts to your real user.
```

This is intentional. Your accounts get created against **your own** Google `sub` the first time you log in. That keeps demo data realistic and forces you to exercise the "create user on first login" path on Day 2.

## JPA mapping

Three entities — one per table. Use `@Entity`, `@Id`, `@Column(name = "...")` for any field whose Java name doesn't match the DB column. Use `BigDecimal` for money. Use `Instant` (or `OffsetDateTime`) for timestamps.

A sketch of `AccountEntity`:

```java
@Entity
@Table(name = "ACCOUNTS")
public class AccountEntity {

    @Id
    @Column(name = "ACCOUNT_ID")
    private String accountId;

    @Column(name = "OWNER_ID", nullable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACCOUNT_TYPE", nullable = false)
    private AccountType accountType;

    @Column(name = "CURRENCY", nullable = false)
    private String currency;

    @Column(name = "BALANCE", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    // protected no-arg constructor for JPA, getters, setters or builder
}
```

Mirror this pattern for `TransactionEntity` and `BankUserEntity`. Do **not** annotate domain entities with REST/Jackson concerns; that's what DTOs are for. (See [API Contract](./03-api-contract.md).)

### Relationships

You have a choice. The recommended approach is **simple foreign-key strings, no `@ManyToOne`/`@OneToMany`**, because:

- Module 7 explicitly warned about N+1 problems with JPA relationships.
- Your service layer is small enough that an explicit `accountRepository.findByOwnerId(userId)` is clearer.

If your team wants `@ManyToOne` relationships (rubric "Exceeds" mentions avoiding N+1), use `fetch = FetchType.LAZY` and add `@Query("... JOIN FETCH ...")` for the listing endpoints. Justify the choice in `docs/architecture.md`.

## Repositories

Spring Data JPA. Interface, no implementation:

```java
public interface AccountRepository extends JpaRepository<AccountEntity, String> {
    List<AccountEntity> findByOwnerId(String ownerId);
}

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
    List<TransactionEntity> findByAccountIdOrderByCreatedAtDesc(String accountId);
    List<TransactionEntity> findByTransferGroupId(String transferGroupId);
}

public interface BankUserRepository extends JpaRepository<BankUserEntity, String> {
    Optional<BankUserEntity> findBySubject(String subject);
}
```

Anything more complex than a finder method goes in `@Query` with named parameters. Don't use the criteria API for anything in this capstone — it's overkill.

## Transactions and consistency

A money transfer between two accounts must be **atomic**: both rows committed or neither. Wrap `TransactionService.executeTransfer(...)` with `@Transactional` (no propagation argument; the default is correct). Your debit-then-credit logic runs inside the same transaction.

For a single deposit or withdrawal, `@Transactional` is still required because you both update the account balance **and** insert a transaction row. Two writes, one transaction.

The Kafka publish is **not** part of the DB transaction. Publish *after* the DB transaction commits — typically by emitting the event from the service method's caller, or using Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)`. See [Kafka Events](./06-kafka-events.md) for the chosen approach.

## Test data without polluting Oracle

For unit tests, do not touch Oracle. Mock `AccountRepository` and `TransactionRepository` and write JUnit tests against the service. For integration tests, the rubric "Meets" criterion says "real persistence layer (or Testcontainers)." Both are acceptable:

- **Real Oracle (lightweight)** — point integration tests at a separate `bankapp_test` Oracle schema; clean tables in `@BeforeEach`. Slow but realistic.
- **Testcontainers Oracle** — `org.testcontainers:oracle-xe`. Slower startup, fully isolated. The bootcamp didn't cover Testcontainers explicitly, so this is the "Exceeds" path.

H2 in-memory **will not work** for any non-trivial query because Oracle dialect differs. Don't try.

Next: [REST API Contract](./03-api-contract.md).
