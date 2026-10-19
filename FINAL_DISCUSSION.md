InFlo — System Overview, Engineering Decisions, and Future Roadmap
1. Introduction
InFlo is a web-based work management application designed for a small organization. It provides a shared workspace where employees can coordinate work items, monitor progress, and manage responsibilities across teams.
The application follows a three-layer architecture:
- Frontend: React, TypeScript, and Vite provide the user interface.
- Backend: Java 21 and Spring Boot expose APIs and enforce application rules.
- Database: PostgreSQL stores application data.
The backend is responsible for authentication, authorization, validation, and business logic. This keeps important rules outside the browser, where they could otherwise be bypassed.
2. Application Architecture
How a request travels through the system
When a user interacts with InFlo, the request follows this sequence:
1. The user signs in through the frontend.
2. The backend verifies the supplied credentials and issues a JSON Web Token (JWT) if authentication succeeds.
3. The frontend includes the token when calling protected backend endpoints.
4. The backend validates the token and checks whether the user is permitted to perform the requested operation.
5. The backend reads or updates data in PostgreSQL.
6. The backend returns a response, and the frontend displays the result.
This separation gives each component a clear responsibility. The frontend focuses on presentation and user interaction, the backend is the trusted enforcement layer, and PostgreSQL provides persistent storage.
Server-side pagination
InFlo uses server-side pagination for work-item listings. Rather than sending the entire dataset to the browser, the backend returns a requested page of results. This reduces unnecessary data transfer and helps keep the application responsive as the number of work items grows.
3. User Roles and Selective Authorization
InFlo is designed around three roles: Manager, Team Lead, and Member. Each role has a dashboard suited to its responsibilities. The exact operations available to each role are determined by the backend authorization rules.
Manager dashboard
The organization overview gives the Manager a broader view of work across the organization. It includes work-item summaries, team navigation, and access to organization-level work views supported by the application.
Team Lead dashboard
The Team Lead role is intended for coordinating work within a team. Its permitted operations are constrained by the team's scope and the rules implemented by the backend.
Member dashboard
The Member role is intended for employees working on assigned items. Members can access the work and actions permitted to their role, while restricted operations remain unavailable.
How authorization is enforced
Authentication and authorization solve different problems:
- Authentication establishes who the user is.
- Authorization determines what the authenticated user may access or change.
The frontend can hide actions that a role should not use, but hiding a button is not a security control by itself. The backend must check the user's role and, where applicable, team membership, assignment, and the requested operation. This prevents a user from gaining access simply by changing a URL or manually sending an API request.
4. Data Integrity and Concurrent Updates
Work management systems may receive repeated requests or simultaneous updates. InFlo includes mechanisms intended to protect against two important problems: duplicate creation and stale updates.
Idempotent work-item creation
An idempotency key identifies a creation request so that a retry can be distinguished from a new operation. InFlo's creation service scopes the key to the requesting actor and calculates a hash of the request data.
- A repeated request with the same key and matching request data can reuse the original result after the original operation has completed.
- Reusing the same key with different request data is rejected as a conflict.
- Invalid keys are rejected.
This approach helps prevent accidental duplicate work items when a client retries a request. The exact guarantees under simultaneous database requests should be confirmed with database-level integration tests.
Optimistic concurrency control
A work item has a version field. When an update supplies an expected version that no longer matches the stored version, the service can reject the update rather than silently overwriting newer data. The database version mechanism also helps detect conflicting writes.
This allows the user or client to refresh the latest data and reconcile the change instead of unknowingly losing another user's update.
Transactions
Database operations that belong together should be performed within a transaction so that partial changes are not committed when an operation fails. Transaction behaviour should be verified for each multi-step workflow, particularly where work-item creation and idempotency records are updated together.
5. Failure Handling and Recovery
A reliable application needs clear behaviour when requests fail. The table below describes the intended handling and the relevant recovery considerations.
Situation	Expected behaviour and recovery
Invalid credentials	Authentication should fail, and the user should be able to correct the credentials and retry.
Missing, invalid, or expired JWT	Protected requests should be rejected. The user may need to sign in again.
Unauthorized operation	The backend should reject the operation rather than trusting the frontend's presentation.
Invalid request data	Validation should reject the request with an appropriate client error.
Stale work-item version	The update should be rejected as a conflict so the latest data can be retrieved before retrying.
Repeated creation request	A matching completed idempotent request can reuse its original result; conflicting key reuse should be rejected.
Database unavailable	The request may fail. The interface should not report an unconfirmed change as saved, and the service should recover when the database is available again.
Network interruption	The interface should communicate the failure and allow a safe retry. Non-idempotent operations require particular care before being retried.
Chat or email delivery failure	These features require delivery tracking, safe retries, and duplicate prevention if added to the application. They should not be assumed to exist unless implemented and verified.


The distinction between intended behaviour and verified behaviour matters. Automated unit tests can verify individual branches of logic, while integration tests are needed to verify database transactions, simultaneous requests, and other end-to-end failure scenarios.
6. MVP Assumptions
The first version of InFlo is based on a deliberately small organizational model.
- Simple office hierarchy: The organization has three main roles—Manager, Team Lead, and Member—instead of a complex enterprise reporting structure.
- Team-based work: Teams provide a practical boundary for assigning work and deciding which users may access it.
- Internal collaboration: A small office may benefit from internal chat connected to teams or work items. Live chat is a potential extension unless it is already implemented in the current build.
- Browser-based application: The MVP focuses on a web interface rather than native mobile applications.
- Relational data model: Users, teams, work items, assignments, and activity history have relationships that benefit from a relational database.
- Core workflow before extras: Authorization, work-item management, pagination, duplicate-request protection, and stale-update handling take priority over advanced collaboration and enterprise-scale operations.
These assumptions keep the initial system manageable while leaving room for additional workflows as real requirements emerge.
7. Most Important Engineering Concerns
The most important concerns in InFlo are correctness, data boundaries, and predictable behaviour under failure.
1. Authorization: Each user must be restricted to the data and operations permitted by their role and scope.
2. Data consistency: Concurrent changes should not silently overwrite newer information.
3. Safe retries: Retried requests should not create duplicate work items.
4. Scalable data access: Server-side pagination avoids loading the full dataset into the browser.
5. Clear feedback: The interface should distinguish loading, empty, successful, and failed operations.
6. Testability: Important business rules and failure paths should be covered by automated tests.
A visually polished interface is useful, but it does not replace secure authorization or consistent data handling. Those foundations are important before adding more advanced collaboration features.
8. Testing and Verification
The project includes a focused unit-test class for the idempotent work-item creation service. The scenarios covered by that test class include:
- Rejecting a blank idempotency key.
- Rejecting a key that exceeds the allowed length.
- Rejecting reuse of a key with different request data.
- Rejecting an idempotency record that has not yet been linked to a work item.
These tests do not, by themselves, prove that every workflow is production-ready. Additional tests should cover successful retries, simultaneous requests against PostgreSQL, transaction rollback, stale updates, role and team authorization, and pagination.
The full backend test suite can be run from the backend directory with:
mvn test
If Maven is not available on the machine, use the Windows Maven Wrapper if present:
.\mvnw.cmd test
Test results should be checked in the Maven output. A passing test run confirms only the scenarios that the tests actually exercise.
9. What Would Change as the Application Grows?
The next stage should be driven by observed usage and measured bottlenecks rather than by adding infrastructure prematurely.
Database and API improvements
- Test query performance and add indexes based on real query patterns.
- Keep filtering and pagination on the server.
- Consider cursor-based pagination if offset pagination becomes expensive at high volumes.
- Use explicit database migrations for controlled schema changes.
- Standardize API errors and maintain backward-compatible contracts where required.
Reliability and security
- Add structured logs, request/correlation IDs, metrics, health checks, and alerts.
- Add integration and end-to-end tests for critical workflows.
- Define backup, restore, retention, and incident-recovery procedures.
- Review token storage, expiration, HTTPS, and secret-management practices.
- Add appropriate rate limiting and abuse protection.
Background processing
Slow or secondary work, such as sending notifications, should not unnecessarily delay a user-facing API request. If notifications are introduced, a durable queue or background job mechanism can provide retries and delivery tracking. The processing should be idempotent so that retries do not send duplicate notifications.
The application should remain a well-structured backend until evidence shows that a component needs to be separated into its own service.
10. Proposed Next-Week Plan
The following roadmap prioritizes a reliable user experience and the most valuable collaboration features.
Priority 1 — Complete the frontend interactions
- Review every visible button, link, menu, and navigation item.
- Make each control perform its advertised action or clearly indicate that it is unavailable.
- Add consistent loading, empty, success, and error states.
- Validate forms and display understandable API error messages.
- Test the Manager, Team Lead, and Member journeys from login through their permitted operations.
Priority 2 — Expand automated tests
- Test successful idempotent retries.
- Test simultaneous requests using the same idempotency key against PostgreSQL.
- Test transaction rollback when a multi-step operation fails.
- Test stale-version and concurrent updates.
- Test role, team, and assignment restrictions.
- Test pagination and filtering with a realistic dataset.
Priority 3 — Add internal live chat
For a small-office MVP, chat could be scoped to teams or individual work items.
- Persist messages in PostgreSQL.
- Check authorization on both message retrieval and message creation.
- Paginate message history.
- Add WebSocket or Server-Sent Events delivery for live updates.
- Handle disconnects, reconnects, and offline states clearly.
Reliable persistence and authorization should come before real-time delivery. The browser must never be the only layer deciding who can read or send messages.
Priority 4 — Add email notifications
Notifications could be triggered by events such as work assignment or status changes.
- Send emails asynchronously so API requests do not wait for delivery.
- Track delivery state and retry transient failures.
- Prevent duplicates with a stable event or idempotency identifier.
- Keep mail credentials in environment variables or a secrets manager.
- Test with a mock or sandbox mail provider before sending real emails.
Live chat and email should be treated as planned features unless the current implementation already supports them end to end.
Priority 5 — Improve production readiness
- Add observability, health checks, and operational alerts.
- Validate required configuration at startup.
- Review authentication and secret-management practices.
- Add database migration and backup/restore procedures.
- Perform security, accessibility, and performance checks.
- Document deployment and recovery procedures.
11. Demonstration Guide
A short demonstration can follow this sequence:
1. Sign in with a verified demo account.
2. Show the Manager organization overview and its work-item summaries.
3. Sign in as a Team Lead and show team-level operations.
4. Sign in as a Member and show assigned work and permitted updates.
5. Demonstrate a rejected unauthorized operation if it has been verified.
6. Show server-side pagination.
7. Explain idempotency and demonstrate the relevant test or API request if prepared.
8. Explain stale-update detection and show a test if prepared.
9. Demonstrate a validation or authorization failure and show the application's response.
10. Close with the MVP assumptions and proposed next steps.
The demo should use known working accounts and prepared sample data. Empty dashboards and unverified behaviours should not be used as evidence that a feature works.
12. Summary
InFlo is an MVP for team-based work management. Its architecture separates presentation, business rules, and persistence. Its key engineering mechanisms include backend authorization, server-side pagination, idempotent creation, and optimistic concurrency control.
The next phase should strengthen automated integration testing and frontend feedback, then add team-scoped live chat and asynchronous email notifications with reliable persistence, authorization, and retry handling.
Throughout development and demonstration, three labels should remain distinct:
- Implemented: the capability exists in the code.
- Verified: the capability has been exercised successfully in a repeatable test or demonstration.
- Planned: the capability is a proposed improvement that has not yet been completed.