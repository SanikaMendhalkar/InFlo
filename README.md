# InFlo — Work Management System

InFlo is a work management application built with React, TypeScript, Spring Boot, and PostgreSQL. It provides role-based access to work management, including work-item assignment, status tracking, and activity history.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Architecture Diagram](#architecture-diagram)
- [Login Page](#login-page)
- [Dashboard Screenshots](#dashboard-screenshots)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Database Configuration](#database-configuration)
- [Authentication and JWT Configuration](#authentication-and-jwt-configuration)
- [Demo Login Credentials](#demo-login-credentials)
- [Running the Application](#running-the-application)
- [Running Automated Tests](#running-automated-tests)
- [Troubleshooting](#troubleshooting)
- [Engineering Decisions](#engineering-decisions)

## Overview

InFlo provides a centralized workspace for managing work items, coordinating teams, tracking status, and monitoring activity. Access to work items and operations depends on the authenticated user's role and the application's authorization rules.

## Features

- Role-based access for Manager, Team Lead, and Member users.
- JWT-based authentication for protected API endpoints.
- Work-item creation, assignment, and status updates.
- Team-aware assignment and authorization rules.
- Server-side pagination and filtering.
- Idempotent work-item creation to help prevent duplicate requests.
- Optimistic concurrency control to detect stale updates.
- Activity and assignment history tracking.
- Organization-level work overview.

## Technology Stack

| Component | Technology |
|---|---|
| Frontend | React, TypeScript, Vite |
| Backend | Java 21, Spring Boot |
| Database | PostgreSQL |
| Authentication | JSON Web Token (JWT) |
| Backend build tool | Maven |
| Frontend package manager | npm |

## Architecture Diagram

The architecture diagram illustrates how the frontend, backend, PostgreSQL database, authentication, and core work-management components interact.

[Open the architecture diagram](docs/images/architecture.png)

## Login Page

The login page provides access to the protected InFlo workspace. Users sign in with their application credentials before accessing features permitted by their role.

[Open the login page screenshot](docs/images/login_page.png)

## Dashboard Screenshots

### Manager Dashboard — Organization Overview

The organization overview provides a centralized view of work across teams. It displays work-item metrics, team navigation, status columns, pagination controls, and backend connection status.

[Open the Manager Dashboard screenshot](docs/images/manager_dashboard.png)

Additional Team Lead and Member dashboard screenshots can be added here as they become available.

## Prerequisites

Install the following before running the application:

- Git
- Java JDK 21
- Maven
- Node.js and npm
- PostgreSQL

Verify the installations:

```bash
git --version
java -version
mvn -version
node --version
npm --version
```

Ensure Java 21 is active and PostgreSQL is running.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/SanikaMendhalkar/InFlo.git
cd InFlo
```

The project contains separate frontend and backend applications.

## Database Configuration

### 2. Create the database

Open PostgreSQL using a client such as pgAdmin or `psql` and execute:

```sql
CREATE DATABASE inflo;
```

The backend connection is configured as:

```text
jdbc:postgresql://localhost:5432/inflo
```

The default configured database username is `postgres`. If your local PostgreSQL installation uses a different username, update `backend/src/main/resources/application.properties` accordingly.

The backend configuration uses Hibernate schema updates:

```properties
spring.jpa.hibernate.ddl-auto=update
```

This setting allows Hibernate to update the schema based on mapped entities. It does not guarantee that demo accounts or application records have been populated. Check the project's data seeder to confirm how initial users and data are created.

## Authentication and JWT Configuration

InFlo uses JWT authentication to protect API endpoints.

### 3. Configure environment variables

Open PowerShell at the repository root and navigate to the backend:

```powershell
cd backend
```

Set the password for your local PostgreSQL database:

```powershell
$env:DB_PASSWORD = "YOUR_POSTGRES_PASSWORD"
```

Replace the placeholder with your actual local PostgreSQL password.

### Configure the JWT secret

The backend reads its signing secret from `INFLO_JWT_SECRET`.

Generate a random secret for a fresh local environment:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:INFLO_JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Run this in the same PowerShell session used to start the backend.

**Do I need a new JWT secret every time?**

No. Generate a secret when configuring a new environment, then reuse it across normal application restarts. Changing the secret generally invalidates tokens signed with the previous secret.

The configured token expiration is `3600000` milliseconds, equivalent to one hour. After expiration, users must log in again unless token-refresh functionality is implemented.

The JWT signing secret is different from a dashboard password. Users authenticate with their account credentials; the backend uses the signing secret to sign and verify JWTs.

### How login works

1. The user enters a username and password.
2. The frontend sends the login request to the backend.
3. The backend authenticates the account and issues a JWT on successful login.
4. The frontend uses the token for subsequent protected API requests.
5. The backend validates the token before granting access.

## Demo Login Credentials

The following local demo credentials are recorded for the project.

| Role | Username | Password |
|---|---|---|
| Manager | `manager@inflo.local` | `InFlo@123` |
| Team Lead | `engineering.lead@inflo.local` | `InFlo@123` |
| Member | `engineering.member1@inflo.local` | `InFlo@123` |

The Manager account was previously verified to log in successfully. Verify the Team Lead and Member accounts against your local database before relying on them for a demonstration.

The recorded seeding convention uses team-specific accounts, including Member accounts numbered from `member1` through `member10`. Existing nonblank passwords may not be overwritten by the seeder, so a previously changed password may differ from the initial demo password.

**Security note:** These credentials are for local demonstration only. Do not use them in production or reuse them for personal accounts. If the accounts are not seeded automatically, follow the account-creation procedure implemented in the project.

## Running the Application

Run the backend and frontend in separate terminals.

### 1. Start the backend

From the repository root:

```powershell
cd backend
```

Set the environment variables in this terminal. If you have already set them in this same session, you do not need to set them again.

```powershell
$env:DB_PASSWORD = "YOUR_POSTGRES_PASSWORD"
```

Generate the JWT secret only if one has not already been configured for this environment:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:INFLO_JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Start Spring Boot using Maven:

```powershell
mvn spring-boot:run
```

The backend should be available at:

`http://localhost:8080`

Keep this terminal open while using the application.

**If Maven is unavailable**, try the Maven Wrapper if the wrapper files are present.

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

If you open a new terminal, configure the environment variables again. Reuse the same JWT secret if you want previously issued tokens to remain valid.

### 2. Start the frontend

Open a second terminal at the repository root:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Vite typically starts the frontend at:

`http://localhost:5173`

Open the exact URL printed in the terminal if it differs.

Keep both terminals running while using the application.

### 3. Log in and explore

1. Open `http://localhost:5173` in your browser.
2. Sign in using a configured demo account.
3. Explore the dashboard available to that role.
4. Try the operations permitted for that role, such as viewing work items, updating status, or managing assignments.

Available actions depend on the backend's authorization rules.

## Running Automated Tests

Run these commands from the `backend` directory.

### Run the full backend test suite

```powershell
mvn test
```

### Run the idempotency service test specifically

```powershell
mvn -Dtest=IdempotentWorkItemCreationServiceTest test
```

### If Maven is unavailable

On Windows, use the Maven Wrapper if present:

```powershell
.\mvnw.cmd test
```

```powershell
.\mvnw.cmd -Dtest=IdempotentWorkItemCreationServiceTest test
```

Review the Maven output for actual pass/fail results. A successful run confirms only the scenarios covered by the tests; it does not mean every integration, concurrency, authorization, or end-to-end scenario has been tested.

## Troubleshooting

### Backend cannot connect to PostgreSQL

- Confirm that PostgreSQL is running.
- Check that the `inflo` database exists.
- Verify the database username and password.
- Confirm that `DB_PASSWORD` is set in the backend terminal.

### Missing environment variable

Set both `DB_PASSWORD` and `INFLO_JWT_SECRET` before launching Spring Boot. PowerShell variables set using `$env:` apply to the current session.

### Login fails

- Confirm that the account exists in the database.
- Check whether the data seeder ran successfully.
- Verify the username and password.
- Inspect backend logs for authentication or database errors.
- Remember that the seeder may not overwrite existing nonblank passwords.

### Frontend cannot connect to the backend

- Confirm that the backend is running on port `8080`.
- Check the frontend API configuration.
- Verify that the configured CORS origin matches the frontend URL, typically `http://localhost:5173`.

### Frontend dependency errors

From the `frontend` directory, run:

```powershell
npm install
npm run dev
```

### Port already in use

Stop the process using the required port or configure the application to use another available port.

## Engineering Decisions

See [ENGINEERING_DECISIONS.md](./ENGINEERING_DECISIONS.md) for details about architecture, database design, authentication and authorization, idempotency, concurrency control, transactions, automated testing, trade-offs, and future improvements.
