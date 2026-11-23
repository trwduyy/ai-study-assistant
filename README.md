# AI Study Assistant – Master Context & Coding Rules

## 1. Project Overview
- **Project Name:** AI Study Assistant (Desktop Application)
- **Goal:** A cross-platform desktop app supporting the study cycle: *Organize → Understand → Practice → Evaluate → Improve*.
- **Scope:** Strictly focus on **Phase 1 (MVP)** unless explicitly instructed otherwise:
    - FR-01: User Auth (Register, Login, Profile, Change Password)
    - FR-02: Course & Topic CRUD
    - FR-03: Material Management (Upload PDF/DOCX/TXT, text extraction)
    - FR-04: AI Summarization (structured summary, selectable length)
    - FR-05: AI Study Chat (document-grounded Q&A + chat history)
    - FR-06 & FR-07: AI Quiz Generator, Quiz Taking, Auto-scoring & Explanations
    - FR-10: Basic Learning Analytics Dashboard
- **Out of Scope for MVP (DO NOT implement unless asked):** RAG/Vector DB, Flashcards, Study Planner, Custom ML models, OCR, Admin/Teacher roles.

---

## 2. System Architecture & Tech Stack
- **Deployment Model:** Desktop Client (Electron) communicates via REST API with a standalone Backend Server (Spring Boot + MySQL). **NEVER** bundle Java/Spring Boot or MySQL inside the Electron installer.
- **Monorepo Structure:**
    - `/backend` — Java 17+, Spring Boot 3.x, Spring Data JPA, Spring Security (JWT), MySQL, Apache PDFBox, Apache POI, Lombok, Springdoc OpenAPI.
    - `/desktop` — Electron, Vite, React, TypeScript, Tailwind CSS, Axios.
    - `/docker-compose.yml` — Local MySQL container setup.

---

## 3. Core Technical & Security Rules

### Backend (Java / Spring Boot)
1. **Layered Architecture:** Strictly separate `controller` → `service` → `repository` → `entity` / `dto`. Never expose JPA `@Entity` classes directly in Controller responses; always use DTOs.
2. **Standard API Response:** Wrap all REST responses in a uniform generic class `ApiResponse<T>`:
   ```json
   {
       "success": true,
       "message": "Operation successful",
       "data": {},
       "timestamp": "2026-11-23T10:00:00Z"
   }
   ```
3. **Exception Handling:** Use a centralized `@RestControllerAdvice` (`GlobalExceptionHandler`) to handle validation errors, auth errors, not found, and external AI API failures cleanly.
4. **Security & Ownership:**
    - Hash passwords with `BCryptPasswordEncoder`.
    - Use stateless JWT authentication.
    - **Resource Ownership:** Every Course, Material, Chat Session, and Quiz query/mutation MUST verify that the resource belongs to the currently authenticated user.
    - **API Key Isolation:** `GEMINI_API_KEY` MUST stay in Backend environment variables only. Never expose it to the client.
5. **File Processing (PDFBox / POI):**
    - Validate file extension, MIME type, and max size (20MB) before saving.
    - Extract and clean text, then persist it in the `materials.extracted_text` (`LONGTEXT`) column.
    - **Empty/Scan Check:** If extracted text is empty or shorter than 50 characters (likely a scanned PDF), return a clear error/warning status instead of sending empty text to Gemini.
6. **Gemini AI Integration:**
    - Centralize all Gemini calls in a single `GeminiClientService` with proper timeout (60s), error logging, and controlled retry on transient errors (e.g., HTTP 429/503).
    - **Quiz Generation:** ALWAYS use Gemini **Structured Outputs (JSON Schema)** to guarantee valid JSON for questions, options, correct answer, and explanation. Validate the parsed output before saving to MySQL.

### Frontend & Desktop (Electron / React / TypeScript)
1. **Strict TypeScript:** Avoid `any`. Define clear interfaces in `/src/types/` matching the Backend DTOs and `ApiResponse<T>`.
2. **API Layer:** Centralize HTTP calls in `/src/services/` using a configured Axios instance with a request interceptor (attach JWT) and response interceptor (handle 401/network errors).
3. **Async UX for AI Tasks:** Every AI action (Summarize, Chat, Generate Quiz) MUST display a loading state, disable duplicate submissions while pending, and show user-friendly error messages on timeout/failure.
4. **Electron Security:** Keep `contextIsolation: true` and `nodeIntegration: false`. Use `preload.ts` for any necessary IPC bridges.

---

## 4. Code Style & Formatting
- **Indentation:** Strictly **4 spaces** across all files (Java, TypeScript, JSON, YAML, CSS).
- **Naming Conventions:**
    - Java: `PascalCase` for classes, `camelCase` for methods/variables, `snake_case` for MySQL tables/columns.
    - TypeScript/React: `PascalCase` for components/types, `camelCase` for functions/variables, `kebab-case` or `PascalCase` consistently per folder.
- **Language:** Write all code, database names, and technical comments in **English**. UI labels/messages can be in **Vietnamese** (consistent across the app).

---

## 5. AI Assistant Behavior Rules
1. **Language:** Communicate and explain solutions to the developer in **Vietnamese**.
2. **No Placeholders:** Do not leave `// TODO: implement this` or skip critical logic when asked to generate a module. Provide complete, runnable code.
3. **File Paths First:** Always state the exact relative file path (e.g., `backend/src/main/java/com/aistudy/...`) before each code block.
4. **Incremental & Verifiable:** When implementing a feature, list the files to create/modify first, then output clean code. If a change affects the DB schema or API contract, explicitly highlight it.
5. **Stay Focused:** Do not refactor unrelated files or introduce unapproved libraries without asking first.