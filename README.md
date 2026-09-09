# Ledger-bank: Core Banking Engine & Admin Console

A distributed financial ledger and transfer engine engineered for high-concurrency balance transfers, transaction idempotency, and audit-grade consistency.

---

<!-- ================================================================= -->
<!-- IMAGE PLACEHOLDER 1: Dashboard / UI Screenshot                    -->
<!-- Upload your dashboard image to docs/images/dashboard.png          -->
<!-- Or replace the src with your GitHub uploaded image URL            -->
<!-- ================================================================= -->
<p align="center">
  <img src="docs/images/dashboard.png" alt="Ledger-Bank Admin Console & Simulation Lab" width="900" />
</p>

<!-- ================================================================= -->
<!-- IMAGE PLACEHOLDER 2: Architecture Diagram (Optional)              -->
<!-- Upload your diagram to docs/images/architecture.png               -->
<!-- Or replace the src with your GitHub uploaded image URL            -->
<!-- ================================================================= -->
<p align="center">
  <img src="docs/images/architecture.png" alt="System Architecture & Ledger Pipeline" width="900" />
</p>

---

## Key Architectural Safeguards

- **Strict Double-Entry Bookkeeping**: Every fund transfer records immutable debit and credit ledger entries under a single transaction header. Account balances always match the sum of their ledger history.
- **Deadlock-Free Pessimistic Locking**: Concurrent transfers between identical account pairs use deterministic lock ordering (by Account UUID) and JPA pessimistic write locks (`PESSIMISTIC_WRITE`), eliminating deadlocks under high load.
- **Dual-Layer Idempotency**:
  - **Redis Layer**: Fast distributed lock (`SET NX EX`) and in-memory receipt caching for sub-millisecond duplicate detection.
  - **PostgreSQL Layer**: Durable primary key constraint fallback that ensures transactions are executed at most once, even during Redis outages.
- **Auditing & Traceability**: MDC-based distributed tracing (`X-Request-ID`) attached to every log statement, paired with Aspect-Oriented (`@AuditLog`) execution timing and metrics.
- **Operational Health Monitoring**: Spring Boot Actuator `/actuator/health` exposes live database and cache component statuses for hosting platforms (e.g., Render, Kubernetes).
- **Isolated Testing Suite**: All automated tests run on an isolated in-memory H2 database in PostgreSQL mode with automatic schema management (`create-drop`), ensuring local databases are never modified by tests.

---

## Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.2, Spring Data JPA, Hibernate, Spring AOP, Actuator |
| **Databases** | PostgreSQL (Primary), Redis (Idempotency Cache), H2 (In-Memory Tests) |
| **Migrations** | Flyway |
| **Frontend** | React 19, Vite, Tailwind CSS, Axios |
| **Testing** | JUnit 5, Mockito, Spring Boot Test, H2 In-Memory DB |

---

## Project Structure

```text
core-banking-app/
├── backend/
│   ├── src/main/java/io/github/aminedelta/core_banking/
│   │   ├── aop/           # Audit logging aspects & annotations
│   │   ├── config/        # CORS, Redis, Request ID filter
│   │   ├── controller/    # Account and Transfer REST controllers
│   │   ├── domain/        # JPA Entities (Account, LedgerEntry, TransactionHeader, etc.)
│   │   ├── dto/           # Request and Response transfer DTOs
│   │   ├── exception/     # Global exception handler & custom exceptions
│   │   ├── repository/    # Spring Data repositories with pessimistic locking
│   │   └── service/       # Business logic (AccountService, TransferService, IdempotencyService)
│   ├── src/main/resources/
│   │   ├── db/migration/  # Flyway SQL schema migrations
│   │   └── application.properties
│   └── src/test/resources/ # Test configuration (isolated H2 in-memory DB)
├── frontend/
│   ├── src/
│   │   ├── App.jsx        # Admin console, live balances, transaction history & backend lab
│   │   └── main.jsx
│   └── package.json
└── README.md
```

---

## Getting Started

### Prerequisites
- **Java 21** or later
- **PostgreSQL** running locally (or via cloud provider)
- **Node.js** (v18+) and **npm**
- *(Optional)* **Redis** on `localhost:6379` (transfers gracefully fall back to PostgreSQL if Redis is offline)

---

### Backend Setup

1. **Configure environment variables** (optional for local defaults):
   ```bash
   cd backend
   cp .env.example .env
   ```

2. **Run the backend**:
   ```bash
   # Windows
   .\mvnw.cmd spring-boot:run

   # Linux / macOS
   ./mvnw spring-boot:run
   ```
   The backend starts at `http://localhost:8080`.

3. **Run the test suite**:
   ```bash
   # Windows
   .\mvnw.cmd test

   # Linux / macOS
   ./mvnw test
   ```

---

### Frontend Setup

1. **Install dependencies**:
   ```bash
   cd frontend
   npm install
   ```

2. **Configure environment variables** (optional for local defaults):
   ```bash
   cp .env.example .env
   ```

3. **Start the development server**:
   ```bash
   npm run dev
   ```
   The interface will be accessible at `http://localhost:5173`.

---

## Environment Variables

### Backend (`backend/.env.example`)
| Variable | Default | Description |
| :--- | :--- | :--- |
| `PORT` | `8080` | Application HTTP server port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/core_banking` | PostgreSQL connection JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `admin123` | Database password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `HIKARI_MAX_POOL_SIZE` | `20` | Maximum database connection pool size |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `*` | Allowed CORS origins (e.g. `https://your-frontend.com`) |

### Frontend (`frontend/.env.example`)
| Variable | Default | Description |
| :--- | :--- | :--- |
| `VITE_API_URL` | `http://localhost:8080` | Base URL of the backend API |

---

## API Reference

### Accounts
- `GET /accounts` — List all registered accounts and current balances.
- `GET /accounts/{accountId}/balance` — Retrieve balance for a single account.
- `GET /accounts/{accountId}/transactions` — Fetch sorted ledger history for an account.
- `POST /accounts` — Create an account with an optional initial balance.

### Transfers
- `POST /transfers` — Execute an atomic funds transfer between two accounts.
  - **Headers**: `Idempotency-Key: <UUID>` *(Required)*
  - **Body**:
    ```json
    {
      "fromAccountId": "11111111-1111-1111-1111-111111111111",
      "toAccountId": "22222222-2222-2222-2222-222222222222",
      "amount": 50.00,
      "description": "Payment transfer"
    }
    ```

### System Health
- `GET /actuator/health` — Returns status of backend, database, and cache.

---

## In the Works & Roadmap

Planned features and architectural enhancements currently queued for development:

- [ ] **Security (API Key Filter on Write Endpoints)**
  - API key authentication filter protecting write operations (`POST /transfers`, `POST /accounts`).
  - Coded from scratch without heavy third-party dependencies for clean, transparent control.
- [ ] **CI Pipeline (GitHub Actions)**
  - Automated continuous integration pipeline: test suite + production build on push and pull requests.
  - Live GitHub build status badge embedded directly into the repository header.
- [ ] **Testcontainers Integration Testing**
  - Spin up real, ephemeral PostgreSQL and Redis Docker containers during integration test runs.
  - Replaces mocks with true-to-life production environment validation.

---

## License
MIT License.