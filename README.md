# AI Study Assistant - Backend Service

A robust, production-ready RESTful backend built with Java 21 and Spring Boot 4. This service powers the AI Study Assistant platform by orchestrating LLM-driven educational workflows, managing secure user authentication, enforcing rate limits, and persisting study session history.

Part of the AI Study Assistant platform:
* Backend Service: [https://github.com/abd-mjahdi/ai-study-assistant-backend](https://github.com/abd-mjahdi/ai-study-assistant-backend)
* Android Client: [https://github.com/abd-mjahdi/ai_study_assistant_android](https://github.com/abd-mjahdi/ai_study_assistant_android)

---

## Architectural Overview

The backend serves as a secure gateway between client applications and Google Gemini. It handles domain logic, user authorization, and data persistence, while delegating raw text processing to the client side.

```
                      +---------------------------------------+
                      |       Android Client (StudyFlow)      |
                      |   (On-device OCR via Google ML Kit)   |
                      +---------------------------------------+
                                          |
                                   HTTPS / REST (JWT)
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| Spring Boot Backend Service                                                       |
|                                                                                   |
|  +------------------------+   +-----------------------+   +--------------------+  |
|  | Spring Security & JWT  |-->| Rate Limiting & Guard |-->|  Study Controller  |  |
|  +------------------------+   +-----------------------+   +--------------------+  |
|                                                                      |            |
|                                                                      v            |
|  +------------------------+   +-----------------------+   +--------------------+  |
|  |  PostgreSQL Database   |<--| Hibernate / Data JPA  |<--|  Gemini Service    |  |
|  | (Users, Sessions, etc) |   +-----------------------+   +--------------------+  |
|  +------------------------+                                          |            |
+----------------------------------------------------------------------|------------+
                                                                       |
                                                                  HTTPS / JSON
                                                                       |
                                                                       v
                                                            +--------------------+
                                                            |  Google Gemini API |
                                                            | (Flash Model)      |
                                                            +--------------------+
```

### Key Engineering Decisions

1. **Client-Side OCR Delegation**: Optical Character Recognition is executed on-device by the Android client using Google ML Kit. The backend receives only extracted plain text. This eliminates image upload bandwidth bottlenecks, reduces server CPU overhead, and enhances user privacy.
2. **Stateless JWT Security**: Authentication uses stateless JSON Web Tokens with HMAC-SHA signing. User sessions require no server-side state, making the service easily scalable.
3. **Structured Prompt Engineering**: The service communicates with Google Gemini using explicit JSON output constraints. Responses are parsed and validated through Jackson into structured Java domain transfer objects before database persistence.
4. **Resilience and Upstream Fault Tolerance**: Calls to Google Gemini include configurable socket timeouts and an exponential backoff retry mechanism (handling HTTP 429 and 5xx errors).
5. **Traffic Protection**: An in-memory token-bucket rate limiter restricts the `/api/study/generate` endpoint per user, preventing upstream API exhaustion and runaway costs. Payloads are checked against a strict byte-size guard.

---

## Tech Stack

| Component | Technology | Version / Specification |
|---|---|---|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 4.0.5 |
| Security | Spring Security, JJWT | 0.12.6 (Stateless JWT, BCrypt) |
| Database | PostgreSQL | 16 (UUID primary keys, cascading deletes) |
| ORM | Spring Data JPA / Hibernate | Object-relational mapping, schema management |
| LLM Integration | Google Gemini API | gemini-flash-latest with structured JSON schemas |
| Build Tool | Maven | Multi-module wrapper (mvnw) |
| Containerization | Docker & Docker Compose | Multi-stage Eclipse Temurin runtime |

---

## System Capabilities

### 1. Authentication and Profile Management
* Account registration and login with BCrypt password hashing.
* Secure stateless JWT issuance and verification.
* Authenticated user profile retrieval and credential update routines.

### 2. AI Content Generation
Processes source text up to 20,000 characters and generates structured study material:
* **Summaries**: Structured overviews with selectable depth (`ESSENTIALS`, `BALANCED`, `THOROUGH`).
* **Quizzes**: Multiple-choice assessments with customizable question counts (3 to 10), four options per question, correct answer index, and comprehensive explanations.
* **Flashcards**: Front-and-back study card pairs formatted for active recall.

### 3. Study Session History
* Paginated and sorted history retrieval scoped to the authenticated user.
* Full study session inspection (including nested questions and flashcards).
* Cascading deletion of historical records.

---

## REST API Specification

### Authentication Endpoints

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Create an account with username, email, and password |
| `POST` | `/api/auth/login` | Public | Authenticate user and receive a signed JWT |
| `GET` | `/api/auth/me` | Protected | Fetch current user account details |
| `PATCH` | `/api/auth/profile` | Protected | Update username or password |

### Study and Generation Endpoints

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/study/generate` | Protected | Generate a summary, quiz, or flashcards from input text |
| `GET` | `/api/history` | Protected | List all study sessions belonging to the authenticated user |
| `GET` | `/api/history/{id}` | Protected | Retrieve full details of a specific study session |
| `DELETE` | `/api/history/{id}` | Protected | Delete a study session and its associated data |

### Error Response Schema

All non-2xx responses return a consistent JSON payload:

```json
{
  "status": 400,
  "message": "Validation failed: text length must be between 20 and 20000 characters"
}
```

Standard error codes handled:
* `400 Bad Request`: Input validation errors.
* `401 Unauthorized`: Missing, expired, or invalid JWT.
* `403 Forbidden`: Authenticated user lacks permission to access the resource.
* `404 Not Found`: Target entity does not exist or does not belong to the user.
* `413 Payload Too Large`: Request payload exceeds size limit (default 128 KB).
* `429 Too Many Requests`: Per-user generation rate limit reached.
* `502 Bad Gateway`: Upstream AI service returned an unrecoverable failure.
* `504 Gateway Timeout`: Upstream AI call exceeded read timeout.

---

## Database Design

```
+--------------------+            +------------------------+
|       users        |            |     study_sessions     |
+--------------------+            +------------------------+
| id (UUID, PK)      |<----+      | id (UUID, PK)          |<----+
| username (VARCHAR) |     |      | user_id (UUID, FK)     |     |
| email (VARCHAR, UQ)|     +------| study_type (ENUM)      |     |
| password (VARCHAR) |            | subject (VARCHAR)      |     |
| created_at (TS)    |            | source_text (TEXT)     |     |
+--------------------+            | summary_content (TEXT) |     |
                                  | created_at (TS)        |     |
                                  +------------------------+     |
                                              |                  |
                       +----------------------+                  |
                       |                                         |
                       v                                         v
        +----------------------------+            +----------------------------+
        |         questions          |            |         flashcards         |
        +----------------------------+            +----------------------------+
        | id (UUID, PK)              |            | id (UUID, PK)              |
        | session_id (UUID, FK)      |            | session_id (UUID, FK)      |
        | question (TEXT)            |            | front (TEXT)               |
        | choices (JSON/VARCHAR)     |            | back (TEXT)                |
        | correct_index (INT)        |            | created_at (TS)            |
        | explanation (TEXT)         |            +----------------------------+
        +----------------------------+
```

* All primary keys use randomly generated UUIDs.
* Foreign keys enforce relational boundaries with `ON DELETE CASCADE`.
* All data retrieval queries are filtered by the authenticated `user_id`.

---

## Environment Configuration

Configuration properties can be set via environment variables or a `.env` file:

| Variable | Default Value | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/aiassistant` | JDBC connection string |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `JWT_SECRET` | *(Required)* | Base64-encoded secret key for signing JWTs |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | JWT lifespan in milliseconds |
| `GEMINI_API_KEY` | *(Required)* | Google Gemini API key |
| `GEMINI_MODEL` | `gemini-flash-latest` | Model target for generation |
| `RATE_LIMIT_GENERATE_CAPACITY` | `10` | Allowed generation requests per user per window |
| `RATE_LIMIT_GENERATE_REFILL_SECONDS` | `60` | Token bucket refill window in seconds |
| `HTTP_REQUEST_MAX_BYTES` | `131072` (128 KB) | Maximum allowable request body size |
| `BACKEND_PORT` | `8080` | Host port for HTTP server |

---

## Getting Started

### Prerequisites
* Java Development Kit (JDK) 21 or higher
* Docker and Docker Compose (recommended for containerized execution)
* Google Gemini API key

### Method 1: Running with Docker Compose (Recommended)

1. Clone the repository and navigate to the project root:
   ```bash
   git clone https://github.com/abd-mjahdi/ai-study-assistant-backend.git
   cd ai-study-assistant-backend
   ```

2. Create your environment configuration:
   ```bash
   cp .env.example .env
   ```
   Set your `GEMINI_API_KEY` and a secure `JWT_SECRET` (Base64 string).

3. Start PostgreSQL and the backend service:
   ```bash
   docker compose up --build
   ```

The application will initialize the database schema and start listening on port `8080`.

### Method 2: Running Locally with Maven

1. Start PostgreSQL (e.g. via Docker):
   ```bash
   docker run --name local-postgres -e POSTGRES_DB=aiassistant -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16-alpine
   ```

2. Set required environment variables:
   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/aiassistant
   export DB_USERNAME=postgres
   export DB_PASSWORD=postgres
   export JWT_SECRET=your_base64_encoded_secret_here
   export GEMINI_API_KEY=your_gemini_api_key_here
   ```

3. Build and launch the application:
   ```bash
   ./mvnw clean spring-boot:run
   ```

---

## Testing

Execute unit and integration tests using Maven:

```bash
./mvnw clean test
```

The test suite validates security filters, repository queries, error handlers, and controller endpoints using MockMvc and Spring Boot Test harnesses.
