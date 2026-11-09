# KnowFlow AI — Knowledge Management & Document Intelligence Platform

[![Java](https://img.shields.io/badge/Java-21-orange.svg?style=flat&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.3.1-blue.svg?style=flat&logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue.svg?style=flat&logo=typescript)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-5.4-purple.svg?style=flat&logo=vite)](https://vitejs.dev/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-3.4-38B2AC.svg?style=flat&logo=tailwind-css)](https://tailwindcss.com/)
[![Tests](https://img.shields.io/badge/Tests-156%20Passed-success.svg)](backend/src/test)

> A full-stack Retrieval-Augmented Generation (RAG) platform designed to ingest multi-format documents, perform high-dimensional vector search, and deliver grounded conversational answers backed by verifiable source citations. Developed as an independent software engineering portfolio project.

---

## Table of Contents

- [1. Overview](#1-overview)
- [2. Problem Statement & Solution](#2-problem-statement--solution)
- [3. Key Features](#3-key-features)
- [4. Technology Stack](#4-technology-stack)
- [5. System Architecture](#5-system-architecture)
- [6. RAG Workflow Explained](#6-rag-workflow-explained)
- [7. Authentication & Role-Based Access Control (RBAC)](#7-authentication--role-based-access-control-rbac)
- [8. Installation & Local Setup (Windows CMD)](#8-installation--local-setup-windows-cmd)
- [9. Configuration & Environment Variables](#9-configuration--environment-variables)
- [10. API Documentation (Swagger / OpenAPI)](#10-api-documentation-swagger--openapi)
- [11. Testing & Quality Assurance](#11-testing--quality-assurance)
- [12. Application Screenshots](#12-application-screenshots)
- [13. Future Roadmap](#13-future-roadmap)
- [14. Author & Acknowledgments](#14-author--acknowledgments)

---

## 1. Overview

**KnowFlow AI** is a full-stack knowledge management and conversational search application. Built with **Spring Boot 3 (Java 21)** on the backend and **React 18 with TypeScript** on the frontend, it bridges the gap between raw, unstructured documents and context-aware conversational assistance.

Teams frequently work with scattered documentation across multiple formats (PDFs, Word documents, text files, policy handbooks, and technical specifications). KnowFlow AI provides workspace-based isolation, multi-stage document ingestion, semantic chunking, dense vector embeddings, and an interactive chat interface that references the exact page and passage supporting each answer.

---

## 2. Problem Statement & Solution

### The Challenge
- **Information Fragmentation**: Crucial documentation is scattered across manuals, handbooks, and policy files, leading to wasted time searching for answers.
- **LLM Hallucinations**: Standard language models generate plausible-sounding but inaccurate answers when queried about domain-specific internal documents.
- **Access Control & Multi-Tenancy**: Unrestricted document search can inadvertently expose private or role-restricted information across teams.
- **Lack of Verification**: Users cannot verify AI responses without clear references back to original source materials.

### The KnowFlow AI Solution
- **Workspace-Level Isolation**: Enforces cryptographic JWT tenant context (`X-Workspace-Id`) and role-based permissions on every database query and vector retrieval.
- **Grounded Responses**: If a query is not covered by authorized documents, the system explicitly reports insufficient evidence rather than guessing.
- **Verifiable Citations**: Every AI response includes clickable, page-referenced citations displaying the exact source snippet.
- **Self-Contained Local Resilience**: Features an intelligent local fallback generator so developers can run and test the complete application offline without requiring external paid LLM credits or third-party APIs.

---

## 3. Key Features

- **Document Ingestion Engine**:
  - Handles `.pdf`, `.docx`, `.doc`, and `.txt` files with automated MIME detection via Apache Tika, PDFBox, and Apache POI.
  - Multi-stage asynchronous pipeline: `UPLOADED` &rarr; `PROCESSING` &rarr; `EXTRACTING` &rarr; `CHUNKING` &rarr; `EMBEDDING` &rarr; `INDEXING` &rarr; `READY`.
  - Content-aware chunking with configurable chunk size and token overlap.
- **Dual Vector Engine Architecture**:
  - **Local Development**: In-memory H2 database with cosine similarity scoring, enabling zero-configuration offline runs.
  - **Production Configuration**: PostgreSQL 16 with the `pgvector` extension utilizing IVFFlat and HNSW indexes for vector retrieval.
- **Retrieval-Augmented Generation (RAG) & Chat**:
  - Hybrid search combining semantic vector embeddings with keyword scoring across authorized chunks.
  - Citation tracking linking assistant answers to specific document chunks, page numbers, and preview snippets.
  - Server-Sent Events (SSE) streaming support via `/stream` with automatic frontend fallback to standard REST responses.
  - User feedback mechanism (thumbs up/down) to assess retrieval groundedness.
- **Document Management & Lifecycle**:
  - Document categorization, folder hierarchy, metadata tagging, and full-text search.
  - In-browser document preview modal displaying extracted text and individual indexed text chunks (page numbers, section headings, and token counts).
  - Secure document download stream.
  - Soft deletion (preserving the physical file and marking a `deletedAt` timestamp) and permanent deletion (removing chunks and document database records). Physical file cleanup is scheduled via Spring `TransactionSynchronizationManager.afterCommit` so file deletion is only attempted after database commit; if filesystem cleanup fails, an orphaned file may remain in storage.
- **Authentication & Security**:
  - Stateless JWT access tokens (24-hour expiration) and refresh tokens (7-day sliding window).
  - HMAC-SHA256 hashed 6-digit password reset codes with 15-minute expiration and 5-attempt rate locking.
  - `token_version` tracking: Immediate revocation of all issued JWT sessions upon password reset.
  - Non-enumerating auth responses (identical responses for existing and non-existent email recovery requests).

---

## 4. Technology Stack

### Backend
- **Core Framework**: Java 21, Spring Boot 3.3.5
- **Security**: Spring Security 6 (Stateless JWT, HMAC-SHA256, BCrypt password hashing)
- **Data Persistence**: Spring Data JPA, Hibernate 6
- **Database (Development)**: H2 in-memory (PostgreSQL compatibility mode)
- **Database (Production Configuration)**: PostgreSQL 16 + `pgvector`, Flyway V1–V4 migrations
- **Document Processing**: Apache Tika 2.9.2, Apache PDFBox 3.0.3, Apache POI 5.3.0
- **Email Delivery**: JavaMail (`spring-boot-starter-mail`) with secure STARTTLS/SSL support in production, console logging fallback in development
- **Documentation**: SpringDoc OpenAPI 2.6.0 (Swagger UI)
- **Testing**: JUnit 5, Mockito, AssertJ, Spring Security Test, Testcontainers (PostgreSQL 16)

### Frontend
- **Framework**: React 18.3.1 with TypeScript 5.6
- **Build Tool**: Vite 5.4 (ESM native, sub-second HMR)
- **Routing**: React Router DOM 6.27
- **State Management**: Zustand 5.0 (Auth, active workspace, theme)
- **Server State & Caching**: TanStack React Query 5.59
- **HTTP Client**: Axios 1.7.7 with automatic 401 token refresh interceptors
- **Styling**: Tailwind CSS 3.4, PostCSS, Lucide React icons
- **Form Validation**: React Hook Form 7.53, Zod 3.23
- **Markdown & Charts**: React Markdown 9.0, Recharts 2.13
- **Testing**: Vitest 2.1, React Testing Library, JSDOM 25

---

## 5. System Architecture

```
+---------------------------------------------------------------------------------+
|                                Frontend (React + Vite)                          |
|  [Auth Pages]   [Workspaces]   [Documents / Preview]   [Chat & Citations]  [Search] |
+----------------------------------------+----------------------------------------+
                                         | REST / JSON / Multipart
                                         v
+---------------------------------------------------------------------------------+
|                             Spring Boot 3.3 Backend API                         |
|                                                                                 |
|  [Security Filter Chain]  --> Validates JWT, token_version, and Workspace ID   |
|                                                                                 |
|  +--------------------+   +-----------------------+   +----------------------+  |
|  |   AuthController   |   |  WorkspaceController  |   |  DocumentController  |  |
|  +--------------------+   +-----------------------+   +----------------------+  |
|  |    ChatController  |   |   SearchController    |   |  Actuator & Swagger  |  |
|  +--------------------+   +-----------------------+   +----------------------+  |
|                                                                                 |
|  [Services Layer]                                                               |
|  - AuthService & PasswordResetService                                           |
|  - WorkspaceService & Authorization Guards                                      |
|  - DocumentService & StorageService (Local Filesystem)                          |
|  - DocumentIngestionPipeline (Tika, PDFBox, POI)                                |
|  - RetrievalService (Semantic + Keyword Hybrid Ranking)                         |
|  - GeminiLlmClient (Cloud Gemini API or Resilient Local Generator)             |
|                                                                                 |
|  [Persistence Layer]                                                            |
|  - Local Dev: In-Memory H2 DB (Zero-config, automated DDL)                      |
|  - Production Configuration: PostgreSQL 16 + pgvector (Flyway Migrations V1-V4) |
+---------------------------------------------------------------------------------+
```

---

## 6. RAG Workflow Explained

Retrieval-Augmented Generation (RAG) ensures that the language model answers questions based strictly on authorized source documents:

```
[1. Document Upload]
         │ (.pdf, .docx, .txt)
         ▼
[2. Text Extraction] (Apache Tika / PDFBox extracts clean, normalized text)
         │
         ▼
[3. Semantic Chunking] (Split into bounded ~800-character passages with 150-char overlap)
         │
         ▼
[4. Vector Embedding] (Passages converted into 768-dimensional dense numerical vectors)
         │
         ▼
[5. Vector Storage] (Stored in database with workspace and access-level boundaries)
         │
================================ USER QUERY TIME ================================
         │
[6. Query Embedding] (User asks: "What is our vacation leave policy?")
         │
         ▼
[7. Hybrid Retrieval] (Top-K chunks retrieved using cosine similarity + keyword match)
         │
         ▼
[8. Prompt Synthesis] (Prompt constructed: Context chunks + User query + Grounding rules)
         │
         ▼
[9. Response & Citations] (AI synthesizes answer and attaches exact document & page references)
```

If no chunks exceed the similarity threshold, KnowFlow AI returns an explicit notice:
> *"I couldn't find sufficient information in your authorized documents to answer this question conclusively."*

---

## 7. Authentication & Role-Based Access Control (RBAC)

KnowFlow AI implements workspace isolation and role permissions:

### Workspace Role Hierarchy

| Role | Workspace Management | Document Upload | Document Preview & Download | Document Deletion | Chat & Search |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **WORKSPACE_ADMIN** | Full (invite, remove, rename) | Yes | Yes | Yes (any document) | Yes |
| **EDITOR** | Read-only | Yes | Yes | Yes (own uploads only) | Yes |
| **VIEWER** | Read-only | No | Yes | No | Yes |

### Session Invalidation Architecture
1. Each user entity maintains an integer `token_version` column (default `1`).
2. When JWT access and refresh tokens are issued, `token_version` is embedded as a claim.
3. Every authenticated request verifies that the token's version matches the user's current version in the database.
4. When a user completes a password reset, `token_version` is atomically incremented. All previously issued tokens are immediately rejected across all sessions.

---

## 8. Installation & Local Setup (Windows CMD)

Follow these step-by-step instructions using standard **Windows Command Prompt (`cmd.exe`)**.

### Prerequisites
- **Java Development Kit (JDK) 21** or higher:
  ```cmd
  java -version
  ```
- **Apache Maven 3.9+**:
  ```cmd
  mvn -version
  ```
- **Node.js (v18+ or v20+) and npm**:
  ```cmd
  node -v
  npm -v
  ```
- **Git**:
  ```cmd
  git --version
  ```

---

### Step 1: Clone the Repository

```cmd
cd /d "C:\Users\YourUsername\Projects"
git clone https://github.com/Shwetayadav108/knowflow-ai.git
cd knowflow-ai
```

---

### Step 2: Start the Backend (Local Profile)

The backend runs out-of-the-box using the `local` profile with an in-memory H2 database. No database installation is required.

1. Navigate to the `backend` directory:
   ```cmd
   cd backend
   ```
2. Set the required JWT signing key (must be at least 32 characters / 256 bits):
   ```cmd
   set JWT_SECRET=knowflow-local-development-secret-key-at-least-32-chars!
   ```
3. Run the Spring Boot application:
   ```cmd
   mvn spring-boot:run
   ```
4. Verify the backend is running by opening:
   - Health check: `http://localhost:8080/actuator/health` (returns `{"status":"UP"}`)
   - Swagger documentation: `http://localhost:8080/swagger-ui.html`

*(Keep this Command Prompt window open).*

---

### Step 3: Start the Frontend

1. Open a **new** Windows Command Prompt window.
2. Navigate to the `frontend` directory:
   ```cmd
   cd /d "C:\Users\YourUsername\Projects\knowflow-ai\frontend"
   ```
3. Install frontend dependencies:
   ```cmd
   npm install
   ```
4. Start the Vite development server:
   ```cmd
   npm run dev
   ```
5. Open your browser and navigate to:
   ```
   http://localhost:5173
   ```

---

### Step 4: First-Time User Walkthrough

1. Click **Sign Up** (`http://localhost:5173/auth/register`) and register your administrator account.
2. Complete the onboarding wizard to initialize your profile and create your first workspace (e.g., `Engineering Team`).
3. Navigate to **Documents** and drag-and-drop a sample `.txt` or `.pdf` file.
4. Watch the status transition from `UPLOADED` to `READY`.
5. Click the **Preview** icon to inspect the extracted text and individual indexed text chunks.
6. Navigate to **Chat**, ask questions regarding your uploaded document, and view the citations returned.
7. To test password recovery, visit `/auth/forgot-password`. Under local development, the 6-digit recovery code is printed directly to the backend console window by `ConsoleLoggingEmailService`.

---

## 9. Configuration & Environment Variables

All settings can be configured via environment variables or specified in `backend/src/main/resources/application.yml`.

### Backend Properties

| Variable | Description | Required? | Default (Local Profile) |
| :--- | :--- | :---: | :--- |
| `JWT_SECRET` | HMAC-SHA256 signing secret for JWT tokens (min 32 bytes) | **Required** | *Must be set via environment* |
| `RESET_TOKEN_SECRET` | Secret used for hashing 6-digit password reset tokens | Optional | Inherits `${JWT_SECRET}` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (`local` or `prod`) | Optional | `local` |
| `STORAGE_LOCAL_DIR` | Filesystem directory for uploaded document storage | Optional | `./storage/documents` |
| `CORS_ALLOWED_ORIGINS` | Permitted origins for frontend communication | Optional | `http://localhost:5173,http://localhost:3000` |
| `GEMINI_API_KEY` | Google Gemini API key for cloud LLM and embedding calls | Optional in dev | *Empty (uses local grounded generator)* |
| `GEMINI_MODEL` | Gemini LLM model identifier | Optional | `gemini-1.5-flash` |
| `GEMINI_EMBEDDING_MODEL` | Gemini embedding model identifier | Optional | `text-embedding-004` |
| `GEMINI_ALLOW_FALLBACK` | Allow fallback to local offline generator if API key absent | Optional | `true` in local, `false` in prod |

### Production Configuration (PostgreSQL + pgvector)

When configuring with PostgreSQL (`SPRING_PROFILES_ACTIVE=prod`), supply these properties:

| Variable | Description | Required in Prod? | Example |
| :--- | :--- | :---: | :--- |
| `DB_HOST` | PostgreSQL host | **Yes** | `localhost` |
| `DB_PORT` | PostgreSQL port | **Yes** | `5432` |
| `DB_NAME` | Database name (with `pgvector` extension enabled) | **Yes** | `knowflow_ai` |
| `DB_USER` | Database username | **Yes** | `knowflow_user` |
| `DB_PASSWORD` | Database password | **Yes** | `strong_db_password` |
| `KNOWFLOW_MAIL_SMTP_HOST` | SMTP mail server host | Optional | `smtp.example.com` |
| `KNOWFLOW_MAIL_SMTP_PORT` | SMTP port (587 for STARTTLS, 465 for SSL) | Optional | `587` |
| `KNOWFLOW_MAIL_SMTP_USERNAME` | SMTP account username | Optional | `notifications@example.com` |
| `KNOWFLOW_MAIL_SMTP_PASSWORD` | SMTP account password | Optional | `smtp_password` |

### Frontend Properties (`frontend/.env`)

| Variable | Description | Required? | Default |
| :--- | :--- | :---: | :--- |
| `VITE_API_BASE_URL` | Base URL pointing to the Spring Boot REST API | Optional | `http://localhost:8080/api/v1` |

---

## 10. API Documentation (Swagger / OpenAPI)

KnowFlow AI provides interactive API documentation generated via SpringDoc OpenAPI.

- **Swagger UI Interactive Console**:
  `http://localhost:8080/swagger-ui.html`
- **OpenAPI 3.0 JSON Specification**:
  `http://localhost:8080/v3/api-docs`

### Major Endpoint Groups

```
Auth Management
├── POST   /api/v1/auth/register             Register new user account
├── POST   /api/v1/auth/login                Authenticate & receive JWT pair
├── POST   /api/v1/auth/refresh              Refresh expired access token
├── GET    /api/v1/auth/me                   Get authenticated user profile
├── PATCH  /api/v1/auth/onboarding-complete  Mark user onboarding as completed
├── POST   /api/v1/auth/forgot-password      Request 6-digit password reset code
└── POST   /api/v1/auth/reset-password       Submit reset code and new password

Workspace & Member RBAC
├── GET    /api/v1/workspaces                List user workspaces
├── POST   /api/v1/workspaces                Create a new workspace
├── GET    /api/v1/workspaces/{id}           Get workspace details
├── PUT    /api/v1/workspaces/{id}           Update workspace details
├── DELETE /api/v1/workspaces/{id}           Delete workspace
├── GET    /api/v1/workspaces/{id}/members   List workspace members and roles
├── DELETE /api/v1/workspaces/{id}/members/{mId} Remove member from workspace
├── POST   /api/v1/workspaces/{id}/invitations Send member invitation
├── GET    /api/v1/workspaces/{id}/invitations List pending invitations
├── DELETE /api/v1/workspaces/{id}/invitations/{invId} Revoke pending invitation
├── GET    /api/v1/invitations/{token}       Validate invitation token
└── POST   /api/v1/invitations/{token}/accept Accept member invitation

Document Lifecycle
├── POST   /api/v1/workspaces/{id}/documents/upload    Upload & enqueue document
├── GET    /api/v1/workspaces/{id}/documents           List & filter documents
├── GET    /api/v1/workspaces/{id}/documents/{docId}   Get document details
├── PUT    /api/v1/workspaces/{id}/documents/{docId}   Update document metadata
├── GET    /api/v1/workspaces/{id}/documents/{docId}/preview Get extracted text & chunks
├── GET    /api/v1/workspaces/{id}/documents/{docId}/download Download physical file
├── POST   /api/v1/workspaces/{id}/documents/{docId}/reprocess Re-extract & re-embed document
├── GET    /api/v1/workspaces/{id}/documents/categories List unique document categories
├── GET    /api/v1/workspaces/{id}/documents/folders   List unique document folder paths
├── DELETE /api/v1/workspaces/{id}/documents/{docId}   Soft-delete document
└── DELETE /api/v1/workspaces/{id}/documents/{docId}?permanent=true Permanent delete

RAG Conversational Chat & Search
├── POST   /api/v1/workspaces/{id}/chat/conversations  Create new conversation
├── GET    /api/v1/workspaces/{id}/chat/conversations  List conversations
├── GET    /api/v1/workspaces/{id}/chat/conversations/search Search conversations by title
├── GET    /api/v1/workspaces/{id}/chat/conversations/{cId} Get conversation details
├── PATCH  /api/v1/workspaces/{id}/chat/conversations/{cId} Rename conversation
├── DELETE /api/v1/workspaces/{id}/chat/conversations/{cId} Delete conversation
├── GET    /api/v1/workspaces/{id}/chat/conversations/{cId}/messages List messages
├── POST   /api/v1/workspaces/{id}/chat/conversations/{cId}/messages Send prompt & get cited answer
├── POST   /api/v1/workspaces/{id}/chat/conversations/{cId}/messages/stream Stream response (SSE)
├── POST   /api/v1/workspaces/{id}/chat/messages/{mId}/feedback Rate answer groundedness
├── POST   /api/v1/workspaces/{id}/chat/compare        Compare two documents using LLM
└── POST   /api/v1/workspaces/{id}/search              Semantic vector & keyword search
```

---

## 11. Testing & Quality Assurance

The codebase is covered by automated unit, integration, and security test suites:

### Backend Verification (JUnit 5 + Mockito + Testcontainers)
```cmd
cd backend
mvn clean test
```

- **Tests Run**: **156 tests**
- **Failures**: **0**
- **Errors**: **0**
- **Skipped**: **2** *(Windows privilege checks for symlinks in LocalStorageServiceTest)*
- **Key Test Areas Covered**:
  - `EndToEndUserJourneyIntegrationTest`: Automated Spring Boot MockMvc integration test executing the complete 10-step user journey (registration, login, refresh tokens, workspace creation, member invitations/RBAC, document upload/ingestion, preview/download, grounded RAG chat, search, and password reset).
  - `PasswordResetTest` & `AuthServiceTest`: HMAC-SHA256 code hashing, 15-minute expiration, 5-attempt locking, and session invalidation.
  - `TenantIsolationTest` & `DocumentAccessControlTest`: Cross-workspace security, role authorization, and folder isolation.
  - `FlywayPostgresMigrationTest`: Automated Testcontainers validation of Flyway schema versions V1 through V4 against PostgreSQL 16.
  - `GlobalExceptionHandlerSecurityTest`: Sanitization of database and filesystem paths from API error responses.

### Frontend Verification (Vitest + TypeScript)
```cmd
cd frontend
npm run lint
npm run test
npm run build
```

- **TypeScript Type Check (`tsc --noEmit`)**: **0 errors**.
- **Vitest Unit & Flow Tests**: **3 test suites, 6 tests passing** (Authentication flow, onboarding pipeline, and chat rendering).
- **Vite Production Build (`npm run build`)**: Generates production bundle assets in `dist/` cleanly without build errors.

---

## 12. Application Screenshots

*Screenshots from local execution:*

### 1. Authentication & Onboarding
```
+-----------------------------------------------------------------------------------+
|                                                                                   |
|                   [ SCREENSHOT PLACEHOLDER: Login & Registration ]                |
|                    Modern split-screen interface with email/password              |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 2. Workspace Dashboard & Document Management
```
+-----------------------------------------------------------------------------------+
|                                                                                   |
|                 [ SCREENSHOT PLACEHOLDER: Documents Management Page ]             |
|          Drag-and-drop zone, file list with status badges, and search filters     |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 3. Document Extracted Text & Chunk Preview
```
+-----------------------------------------------------------------------------------+
|                                                                                   |
|                 [ SCREENSHOT PLACEHOLDER: Document Preview Modal ]                |
|       Tabbed modal inspecting extracted text and individual indexed text chunks   |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 4. Grounded Chat Interface with Verifiable Citations
```
+-----------------------------------------------------------------------------------+
|                                                                                   |
|                 [ SCREENSHOT PLACEHOLDER: Conversational RAG Chat ]               |
|          AI assistant answer with highlighted, clickable source citations & badges |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 13. Future Roadmap

These items represent planned enhancements for future iterations:

1. **Visual Document Comparison (Side-by-Side Diff)**:
   - Full split-pane visual diff viewer for the existing backend comparison endpoint (`/chat/compare`), highlighting contract and specification revisions across document versions.
2. **WebSocket Real-Time Ingestion Updates**:
   - Transitioning ingestion progress monitoring from HTTP polling to full bidirectional WebSocket events for immediate notifications.
3. **Cloud Vector Store Adapters**:
   - Adding modular adapters for managed vector databases (e.g., Pinecone, Qdrant) alongside the native PostgreSQL `pgvector` implementation.

---

## 14. Author & Acknowledgments

### Author
- **Shweta Yadav**
  - GitHub: [@Shwetayadav108](https://github.com/Shwetayadav108)

### Acknowledgments
- **Spring Boot & PostgreSQL Communities**: For documentation and tooling around Spring Boot 3 and `pgvector`.
- **Google Gemini API**: For generative AI and embedding interfaces.
