# AI Study Assistant — Backend HTTP API

Machine-readable reference for **Android / web frontends** and **AI codegen**.  
**Base URL**: configurable (default `http://localhost:8080` when running locally or via Docker Compose `BACKEND_PORT`).

---

## Conventions

| Topic | Detail |
|--------|--------|
| **Format** | `Content-Type: application/json` on requests with a body |
| **Auth** | Stateless **JWT**. Send `Authorization: Bearer <token>` on every protected route |
| **CORS** | Enabled; allowed origins controlled by server (`CORS_ALLOWED_ORIGIN_PATTERNS`, default `*`) |
| **CSRF** | Disabled (JWT API) |
| **Correlation** | Optional request id: send `X-Request-Id` (UUID); echoed on response when generated |
| **Timestamps** | `createdAt` fields are JSON **ISO-8601** strings (e.g. `2026-04-29T21:00:00`) |

### JWT

- **Subject (`sub`)**: user **email** (normalized to lowercase at register/login).
- **Signing secret**: `JWT_SECRET` must be a **valid Base64** string (used as HMAC key bytes). Generate a long random secret and Base64-encode it, or use a long Base64 secret from your secrets manager.

### Public vs protected routes

| Public (no JWT) | Protected (JWT required) |
|-----------------|---------------------------|
| `POST /api/auth/register` | Everything else, including `GET /api/auth/me` |
| `POST /api/auth/login` | |
| Spring Boot `/error` (framework) | |

---

## Error responses

Most errors use this JSON shape:

```json
{
  "status": 400,
  "message": "string"
}
```

| HTTP | When |
|------|------|
| **400** | Validation (`field: message`), bad request |
| **401** | Missing/invalid JWT (Spring Security entry point — same JSON shape) |
| **403** | Authenticated but not allowed (same JSON shape) |
| **404** | Resource not found (e.g. user/session) |
| **413** | `POST /api/study/generate` body too large (`http.request.max-bytes`, default **131072** bytes) |
| **429** | Rate limit on `POST /api/study/generate` (per user; default **10** requests per **60** seconds) |
| **502** | Upstream / Gemini / bad gateway style failures (`BAD_GATEWAY`) |
| **504** | Gemini call timeout (`GATEWAY_TIMEOUT`) |
| **500** | Unhandled server error |

---

## Types

### `StudyType` (enum, JSON string)

`SUMMARY` | `QUIZ` | `FLASHCARDS`

---

## Endpoints

### Auth

#### `POST /api/auth/register` — Create account

**Auth**: none  

**Request body**

```json
{
  "username": "string (1–100 chars)",
  "email": "string, valid email, max 255",
  "password": "string, 8–255 chars"
}
```

**Response `201 Created`**

```json
{
  "token": "jwt_string",
  "userId": "uuid",
  "username": "string"
}
```

---

#### `POST /api/auth/login` — Sign in

**Auth**: none  

**Request body**

```json
{
  "email": "string, valid email, max 255",
  "password": "string, max 255"
}
```

**Response `200 OK`**

Same shape as register:

```json
{
  "token": "jwt_string",
  "userId": "uuid",
  "username": "string"
}
```

---

#### `GET /api/auth/me` — Current user (JWT sanity check)

**Auth**: **Bearer JWT required**

**Response `200 OK`**

```json
{
  "userId": "uuid",
  "username": "string",
  "email": "string"
}
```

---

### Study — AI generation

#### `POST /api/study/generate` — Generate summary, quiz, or flashcards

**Auth**: **Bearer JWT required**

**Request body**

```json
{
  "text": "string, 20–20000 chars (plain text from client; OCR is on-device)",
  "type": "SUMMARY | QUIZ | FLASHCARDS",
  "subject": "optional string, max 120 chars"
}
```

**Response `200 OK`** — one unified record; **unused fields are `null`**.

| `type` | Populated fields |
|--------|------------------|
| `SUMMARY` | `sessionId`, `type`, `subject`, `summary`, `createdAt` — `questions` / `flashcards` are `null` |
| `QUIZ` | `sessionId`, `type`, `subject`, `questions[]`, `createdAt` — `summary` / `flashcards` are `null` |
| `FLASHCARDS` | `sessionId`, `type`, `subject`, `flashcards[]`, `createdAt` — `summary` / `questions` are `null` |

**`questions[]` item** (`QuizQuestionResponse`)

```json
{
  "question": "string",
  "choices": ["A", "B", "C", "D"],
  "correctIndex": 0,
  "explanation": "string or null"
}
```

**`flashcards[]` item** (`FlashcardResponse`)

```json
{
  "front": "string",
  "back": "string"
}
```

**Example — SUMMARY**

```json
{
  "sessionId": "uuid",
  "type": "SUMMARY",
  "subject": "Biology",
  "summary": "string",
  "questions": null,
  "flashcards": null,
  "createdAt": "2026-04-29T21:00:00"
}
```

**Server expectations (generation)**

- Quiz: **5** questions, each **4** choices, `correctIndex` in `0..3`.
- Flashcards: **8** cards.
- Session is **persisted** for history.

---

### History

All routes require **Bearer JWT**. Users only see **their own** sessions.

#### `GET /api/history` — List sessions (metadata only)

**Response `200 OK`** — JSON array of:

```json
{
  "sessionId": "uuid",
  "type": "SUMMARY | QUIZ | FLASHCARDS",
  "subject": "string or null",
  "createdAt": "2026-04-29T21:00:00"
}
```

Order: **newest first**.

---

#### `GET /api/history/{sessionId}` — Full session

**Path**: `sessionId` = UUID  

**Response `200 OK`** — same shape as `POST /api/study/generate` (`GenerateStudyResponse`).

---

#### `DELETE /api/history/{sessionId}` — Delete session

**Path**: `sessionId` = UUID  

**Response `204 No Content`** — empty body.

---

## Android emulator note

If the backend runs on **your dev machine** and the app runs in the **Android emulator**, use **`10.0.2.2`** instead of `localhost` to reach the host (e.g. `http://10.0.2.2:8080`).  
If both backend and app use **Docker Compose networking**, use the service name / published port from Compose instead.

---

## Quick reference table

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/auth/register` | No |
| POST | `/api/auth/login` | No |
| GET | `/api/auth/me` | Yes |
| POST | `/api/study/generate` | Yes |
| GET | `/api/history` | Yes |
| GET | `/api/history/{sessionId}` | Yes |
| DELETE | `/api/history/{sessionId}` | Yes |

---

## Run locally (teammates)

See repository root **`.env.example`** and **`docker-compose.yml`**: copy `.env.example` → `.env`, fill secrets, then `docker compose up --build`.
