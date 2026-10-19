# InFlo — Engineering Decisions

## What's Covered in This Document

This document explains the key engineering decisions behind InFlo, including:

- **Architecture & Data Management:** Technology stack choices, relational database design, and server-side pagination.
- **Authentication & Authorization:** JWT authentication and role-based access control.
- **Idempotency:** Preventing duplicate work-item creation requests.
- **Concurrency Control:** Versioning to detect stale updates and manage conflicting changes.
- **Transactions & Business Rules:** Data consistency, work-item assignment, and activity history.
- **Automated Testing & Verification:** Unit tests for idempotency validation and conflict scenarios, verified test results, and planned tests for concurrency, authorization, and business rules.
- **Trade-offs & Future Scope:** Current limitations, testing gaps, and potential improvements.

## 1. Overview

InFlo is a work-item management application built with a React and TypeScript frontend and a Java Spring Boot backend. The backend uses PostgreSQL for persistent storage and JWT-based authentication for protected API access.

The implementation prioritises correctness, controlled data access, and maintainability over adding unnecessary infrastructure. The decisions below explain the architecture, the risks addressed, the testing performed so far, and the remaining work.

## 2. Architecture and Key Decisions

### 2.1 Relational Database and Server-Side Pagination

**Decision:** Use PostgreSQL as the primary database and retrieve work items through a paginated backend API.

**Reasoning:**
- Work items, users, teams, assignments, and activity records have relationships that benefit from relational constraints.
- Server-side pagination avoids loading the entire dataset into the browser.
- Filtering, sorting, and pagination can be performed by the backend and database as the dataset grows.
- Persistent storage allows work-item state to survive application restarts.

**Trade-off:** Relational modelling and database queries require more upfront design than storing all application data in a single in-memory structure.

**Verification status:** The paginated work-items API has been exercised locally. Broader performance testing with a large dataset remains future work.

### 2.2 Authentication and Authorization

**Decision:** Protect backend endpoints using JWT authentication and Spring Security.

The security configuration permits access to the login endpoint and handles preflight OPTIONS requests. Other protected requests require authentication.

The service layer also applies role- and resource-level rules, including restrictions based on team membership and work-item assignment.

**Reasoning:**
- Authentication establishes the identity associated with a request.
- Authorization determines whether that identity is allowed to perform the requested operation.
- Enforcing restrictions on the backend prevents the frontend from being the sole security boundary.

**Trade-off:** Authorization rules must remain consistent across endpoints and service methods as the application evolves.

**Verification status:** The security configuration and service-level authorization rules are implemented. Dedicated automated tests for unauthorized access, cross-team access, and role restrictions remain future testing work.

### 2.3 Idempotent Work-Item Creation

**Decision:** Support an `Idempotency-Key` when creating work items.

The backend stores an idempotency record associated with the actor and request key. It calculates a SHA-256 fingerprint of the request data to distinguish a legitimate retry from reuse of the same key with different data.

The implementation uses PostgreSQL's `INSERT ... ON CONFLICT DO NOTHING` to handle an existing key. When the key has already been used:
- Matching request data can return the original work item.
- Different request data results in a conflict.
- An incomplete original request results in a conflict response rather than silently creating another work item.

Invalid keys, including blank keys and keys longer than 100 characters, are rejected.

**Reasoning:** Clients may retry a request because of a timeout or a lost response. Idempotency reduces the risk of creating duplicate work items during retries.

**Trade-off:** Idempotency requires additional database state and request-fingerprint logic. The fingerprint must remain consistent with the fields that define a creation request.

**Verification status:** Automated unit tests have been run successfully for invalid keys, reuse of a key with different request data, and an incomplete original request. These tests use mocks and do not independently verify PostgreSQL behaviour or simultaneous requests. Successful repeated-request behaviour and database-level concurrency require further verification.

### 2.4 Optimistic Concurrency for Updates

**Decision:** Use a version field on work items and require the expected version when updating them.

The service checks the supplied version against the current version. A stale version is rejected with a conflict response. The entity also uses JPA's `@Version` mechanism to help detect concurrent database updates.

**Reasoning:** Two users may read the same work item and then submit changes. Without concurrency protection, one update could silently overwrite the other's work.

**Trade-off:** Clients must handle conflicts by refreshing the work item and deciding whether to retry their changes.

**Verification status:** The version-checking logic is implemented. Automated tests for stale updates, simultaneous database writes, and the resulting API responses remain future work.

### 2.5 Transactions and Consistent State

**Decision:** Use transactional boundaries for idempotent work-item creation and database-backed state changes where appropriate.

**Reasoning:** Related writes, such as recording an idempotency key and associating it with the created work item, should not be treated as independent operations when they represent one logical request.

**Trade-off:** Transactions introduce database overhead and require care around transaction boundaries, error handling, and external side effects.

**Verification status:** Transaction annotations and database operations are implemented. Failure-injection and rollback tests remain future work.

### 2.6 Assignment Rules and Activity History

**Decision:** Apply assignment and status-transition rules in the backend service layer and record relevant changes in activity and assignment-history records.

The implemented logic includes role restrictions, team checks, assignment eligibility, and restrictions on reopening closed work items.

**Reasoning:** Business rules should not depend on the frontend behaving correctly. Recording changes also improves traceability.

**Trade-off:** Maintaining activity and history records adds database writes and requires consistency between the primary operation and its audit records.

**Verification status:** These rules and recording operations are implemented in the service layer. Dedicated automated tests for each business rule and transactional consistency remain future work.

## 3. Testing Strategy

### 3.1 Testing Philosophy

The goal is not to achieve an arbitrary code-coverage percentage. Testing should prioritise behaviours where incorrect results could cause duplicate records, lost updates, unauthorized changes, or inconsistent data.

The testing strategy therefore focuses on high-risk behaviours in the current architecture rather than attempting to test every method equally.

### 3.2 Automated Tests Executed

The following idempotency unit-test scenarios have been executed successfully:

| Scenario | Expected behaviour | Status |
|---|---|---|
| Blank idempotency key | Reject with HTTP 400 | Verified by unit test |
| Key longer than 100 characters | Reject with HTTP 400 | Verified by unit test |
| Existing key reused with different request data | Reject with HTTP 409 | Verified by unit test |
| Existing request has no completed work-item association | Reject with HTTP 409 | Verified by unit test |

These tests exercise the idempotency service using mocked dependencies. They validate the relevant decision paths but do not prove database integration, endpoint behaviour, or concurrent-request correctness.

The backend test command used was:

`.\mvnw.cmd -Dtest=IdempotentWorkItemCreationServiceTest test`

The command completed successfully.

### 3.3 High-Risk Tests Remaining

The following tests are prioritised because failures could affect data integrity or security.

**Priority 1 — Data integrity**
- Repeating a successful creation request with the same key and payload returns the original work item.
- Reusing a key with different data never creates an additional work item.
- Concurrent requests using the same key result in only one work item being created.
- A failed creation transaction does not leave an inconsistent idempotency record.
- A stale update is rejected without overwriting newer data.

**Priority 2 — Authorization**
- Unauthenticated requests cannot access protected endpoints.
- Members cannot modify work items assigned to other users.
- Users cannot access or modify work items outside their permitted team.
- Members cannot perform manager-restricted operations.
- Authorization failures return the appropriate HTTP status.

**Priority 3 — Business rules and API contracts**
- Closed work items cannot be reopened if the rule prohibits it.
- Invalid assignments and cross-team parent relationships are rejected.
- Pagination parameters, validation errors, and missing resources produce appropriate responses.
- Activity and assignment-history records reflect successful changes and remain consistent after failures.

**Priority 4 — Scalability and reliability**
- Pagination remains efficient with a substantially larger dataset.
- Database-backed concurrency behaviour is verified with integration tests.
- Relevant database queries are checked for performance and indexing needs.

### 3.4 Testing Approach

The planned test suite will use several levels of testing:

1. **Unit tests:** Test service decisions with mocked repositories and collaborators. These are appropriate for validation, conflict handling, and role-based decision paths.
2. **Integration tests:** Exercise the backend with a real or isolated PostgreSQL instance to verify transactions, constraints, repository queries, and optimistic locking.
3. **API/security tests:** Exercise HTTP endpoints to verify status codes, authentication, authorization, and request validation.
4. **Concurrency tests:** Submit overlapping requests or updates to verify idempotency and stale-write protection under contention.
5. **Performance tests:** Use a larger dataset to examine pagination and query behaviour before making claims about scalability.

These tests are complementary. Passing a mocked unit test does not establish that a database transaction, HTTP security filter, or concurrent request behaves correctly in production.

## 4. Known Limitations and Future Scope

The current implementation focuses on the core work-item workflow and its backend correctness mechanisms. The following are improvements or verification tasks rather than claims of completed functionality.

- **Expanded automated coverage:** Add the high-risk tests listed above, prioritising duplicate prevention, stale updates, and authorization.
- **Database integration tests:** Verify idempotency and optimistic locking against PostgreSQL, including rollback and concurrent requests.
- **End-to-end verification:** Test the full path from the frontend through authentication and the API to the database.
- **Observability:** Add structured logging and useful metrics for failed requests, conflicts, and database errors.
- **Asynchronous processing:** Introduce a queue or background worker if notifications or other secondary tasks become necessary. These tasks do not currently require an asynchronous architecture solely for the sake of adding one.
- **Performance validation:** Measure query performance and pagination under realistic data volumes before choosing further optimisations.
- **Operational hardening:** Review production secret management, database migrations, deployment configuration, backup procedures, and error handling before production use.

## 5. Summary

InFlo uses a relational database, backend-enforced access rules, idempotent creation, optimistic concurrency checks, and transactional database operations to address important correctness risks.

Initial automated tests have verified selected idempotency error paths. The remaining high-risk behaviours—especially successful retries, database-level concurrency, stale-update rejection, and resource-level authorization—are identified for further automated testing.

The project distinguishes implemented logic from behaviour that has been independently verified. Future work will focus on closing those verification gaps before making stronger reliability or scalability claims.