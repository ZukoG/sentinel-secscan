# Software Requirements Specification: Sentinel

**Status:** Draft (updated Day 12)
**Version:** 0.3.0

## 1. Introduction

### 1.1 Purpose
This document specifies the functional and non-functional requirements for
Sentinel, a passive web security assessment platform. It is the anchor
document for the project: the domain model (Day 2), scoring rules (Week 2),
and threat model (Day 15) all derive from what's defined here. If this
document and the code disagree, update this document to match a deliberate
decision. Don't just let it drift.

### 1.2 Scope
Sentinel lets a registered user submit a website URL and receive an
automated, **passive-only** security assessment. That means a set of
findings across several checks (HTTPS usage, security headers, SSL/TLS
certificate status, HSTS, cookie flags, redirect behavior), an aggregate
score and risk rating, and a downloadable PDF report. Users can also view
scan history and track score trends for a website over time.

Sentinel is built as a joint submission for two university electives:

| Elective | What it's graded on |
|---|---|
| Cybersecurity (primary) | Correctness and depth of the passive checks, scoring model, threat model, secure coding (auth, input handling) |
| Cloud Computing (secondary) | Containerization, CI/CD, IaC (Terraform/k8s), architecture documentation. Must be independently reviewable without reading the scanner logic. |

### 1.3 Definitions
- **Check**: a single passive assessment rule (e.g. `HttpsCheck`), implemented via the `ScanCheck` strategy interface.
- **Finding**: the result of one check run against one website, including severity, description, and recommendation.
- **Scan**: one execution of all registered checks against one website, producing a set of findings, a score, and a risk rating.
- **Passive assessment**: inspection of a target's *observable, already-exposed* configuration (response headers, cert metadata, cookie flags, redirect chains). No requests are sent that a normal browser visit wouldn't also send. No fuzzing, brute forcing, injection, or exploitation of any kind.

### 1.4 References
- `docs/THREAT_MODEL.md` (Day 15, STRIDE analysis)
- `docs/CLOUD_ARCHITECTURE.md` (Day 19, target cloud architecture and current deployment status)
- `docs/DEPLOYMENT.md` (Day 19, deployment steps for both local and target cloud modes)
- `docs/MONITORING.md` (Day 19, monitoring plan)
- Project wiki, Architecture page: component, class, sequence, and data model diagrams
- Project wiki, Build Plan page, and the GitHub Issues/Milestones boards: day-by-day build plan and tracking

## 2. Overall Description

### 2.1 Product Perspective
Sentinel is a new, standalone system: a Spring Boot REST API backed by
PostgreSQL, with a React frontend added later in the timeline. It doesn't
integrate with or depend on any existing product.

### 2.2 System Architecture (summary)
```
React Frontend  >>HTTPS/JWT>>  REST API (Spring Boot)
                                  |-- Auth Module (JWT + BCrypt)
                                  |-- Scan Service --> Scanner Engine --> ScanCheck implementations
                                  |               \--> Scoring Service
                                  |               \--> Report Service (PDF)
                                  \-- PostgreSQL (via Flyway managed schema)

Infra layer (independent of the above): Docker/Compose, GitHub Actions CI
(build, test, SAST, dependency scanning), Terraform + k8s manifests
(documentation grade), monitoring.
```
Full diagram: project wiki, Architecture page.

### 2.3 User Classes
- **Registered user**: registers an account, adds websites they own or are authorized to assess, triggers scans, views findings/scores/history, downloads PDF reports.
- No admin/operator role is planned for v1 scope. `role` exists on `User` for future extension only.

### 2.4 Operating Environment
- Backend: Java 25, Spring Boot, runs in Docker (local dev via Docker Compose; documentation grade Terraform/k8s for cloud deployment).
- Database: PostgreSQL, schema managed via Flyway migrations.
- Client: any modern browser (React SPA).

### 2.5 Design and Implementation Constraints
- **Passive only, permanently.** No active scanning, exploitation, brute force, or fuzzing capability may be added at any point, including behind a flag or "advanced mode." This is a project defining constraint, not a v1 limitation. See NFR-1.
- The Strategy pattern (`ScanCheck` interface plus `ScannerEngine`) is the required extension point for new checks. New checks must not require changes to `ScannerEngine` itself (open/closed principle).
- Passwords are stored only as BCrypt hashes. Authentication is JWT based, with no server side session state.

### 2.6 Assumptions and Dependencies
- Users only register websites they own or are authorized to assess. Sentinel does not verify domain ownership in v1. This is called out explicitly in the threat model (Day 15) as an accepted risk with a documented mitigation path (e.g. future DNS TXT record verification), not something silently ignored.
- Target websites are reachable over the public internet at scan time. There's no support for scanning internal or private network targets.

## 3. Functional Requirements

Each requirement is tagged with the plan day it's expected to land. The
build plan itself, and live status per day, is tracked in the project
wiki's Build Plan page and in the corresponding GitHub issue, so this doc
and project status stay traceable to each other.

### 3.1 Authentication & Accounts (Day 4)
- **FR-1.1** A user can register with an email and password. Passwords are hashed with BCrypt before storage; plaintext passwords are never persisted or logged.
- **FR-1.2** A user can authenticate with email and password and receive a JWT.
- **FR-1.3** All endpoints except registration and login require a valid JWT.

### 3.2 Website Management (Day 6)
- **FR-2.1** An authenticated user can register a website (URL) they want assessed.
- **FR-2.2** A user can list, view, and remove websites they own.
- **FR-2.3** A user cannot view or act on websites owned by another user.
- **FR-2.4** Registered URLs are validated (well formed, `http`/`https` scheme only) before being persisted.

### 3.3 Scanner Engine (Days 7 to 9)
- **FR-3.1** The system provides a pluggable scanner engine. Each check implements a common `ScanCheck` interface and is registered with `ScannerEngine`, which runs all registered checks against a website and collects their findings.
- **FR-3.2** Checks implemented in v1 (all passive):
  | Check | What it inspects |
  |---|---|
  | HTTPS usage | Whether the site is served over HTTPS and whether HTTP is redirected to HTTPS |
  | Security headers | Presence and correctness of headers such as `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` |
  | SSL/TLS certificate | Certificate validity window, expiry, issuer chain |
  | HSTS | Presence and configuration of `Strict-Transport-Security` |
  | Cookie security | `Secure`, `HttpOnly`, `SameSite` flags on observed cookies |
  | Redirect analysis | Redirect chain behavior (e.g. insecure intermediate hops, redirect loops) |
- **FR-3.3** Each finding records a check name, severity, human readable description, and a remediation recommendation.
- **FR-3.4** A failed or errored check (e.g. target unreachable) is recorded as a finding/status. It must not be allowed to silently abort the whole scan.

### 3.4 Scoring (Day 11)
- **FR-4.1** After all checks run, the system computes a single aggregate numeric score from the findings. Score starts at 100 and is reduced by a fixed deduction per finding based on its severity (`INFO` 0, `LOW` 5, `MEDIUM` 15, `HIGH` 25, `CRITICAL` 40), floored at 0. Weighting is severity based only, not also weighted per check: each check already assigns its own severity based on domain knowledge of how bad that specific outcome is, a separate per-check multiplier on top would need its own justification not currently available.
- **FR-4.2** The system maps the score to a `RiskRating` enum via fixed bands: 90 to 100 `LOW`, 70 to 89 `MEDIUM`, 40 to 69 `HIGH`, 0 to 39 `CRITICAL`.
- **FR-4.3** Scoring weights and bands are defined in one place (`ScoringService`) so they can be justified and revisited without touching check implementations. The specific numbers above are a first-pass calibration, not derived from an external standard, and may be tuned later with real-world data.

### 3.5 Scan Orchestration & History (Day 12, Day 14)
- **FR-5.1** A user can trigger a scan for a website they own. The triggering request returns immediately once the scan is created (status `IN_PROGRESS`); the scan itself runs asynchronously on a dedicated thread pool (`ScanRunner`, `@Async`), not on the request thread. A worst case scan (six checks, some with their own timeouts) can take 20-30+ seconds, too long to hold an HTTP request open safely.
- **FR-5.2** A scan has a status (e.g. `IN_PROGRESS`, `COMPLETED`, `FAILED`) that a user can poll or view.
- **FR-5.3** A user can view the full history of scans for a website and see score trend over time.

### 3.6 Reporting (Day 13)
- **FR-6.1** A user can generate and download a PDF report for a completed scan, containing the score, risk rating, and full list of findings with recommendations.

## 4. Non-Functional Requirements

- **NFR-1 (Safety, hard constraint).** The system must never send requests to a target beyond what a passive assessment requires (equivalent to a normal browser visit plus certificate/header inspection). No brute forcing, fuzzing, injection, exploitation, or denial of service behavior, ever, under any configuration. This is the project's core premise, and violating it invalidates the "passive assessment platform" framing entirely.
- **NFR-2 (Auth security).** Passwords hashed with BCrypt. JWTs signed and time limited. No secrets committed to the repo (see `.gitignore`).
- **NFR-3 (Data isolation).** A user must never be able to read or modify another user's websites, scans, findings, or reports.
- **NFR-4 (Extensibility).** Adding a new passive check must not require modifying `ScannerEngine` or existing checks (Strategy pattern, open/closed principle).
- **NFR-5 (Portability).** Build and run instructions must work on both Windows (PowerShell/Git Bash) and Linux, since development happens across both.
- **NFR-6 (Resilience).** A single check failing (timeout, unreachable target, malformed response) must not crash the scan or take down other checks' results.
- **NFR-7 (Deployability).** The application must run via `docker-compose up` from a clean checkout with no manual setup beyond documented environment variables.

## 5. Data Model (summary)

Full ERD: project wiki, Architecture page. Entities: `User` (1 to many) `Website` (1 to many)
`Scan` (1 to many) `Finding`; `Scan` (1 to 1) `Report`. These are
implemented as JPA entities plus a Flyway migration on Day 2. This SRS is
the source of truth for what each entity needs to support, so the schema
shouldn't grow fields this document doesn't justify.

## 6. Out of Scope (v1)

- Active/offensive scanning of any kind (see NFR-1; this is a permanent constraint, not a v1 limitation).
- Domain ownership verification for registered websites (accepted risk, documented in the threat model).
- Admin/operator roles and multi-tenant organization accounts.
- Real-time/streaming scan progress (polling is sufficient for v1).
- Scanning targets on private/internal networks.

## 7. Open Questions

None currently open. Both questions originally listed here are resolved, see section 8.

## 8. Resolved Questions

- Exact scoring weights per check and score to risk-rating band boundaries: resolved Day 11, see FR-4.1/FR-4.2 above and `ScoringService`. A known limitation carried forward rather than solved here: a check that couldn't complete (e.g. a "could not verify" `MEDIUM` fallback) currently scores the same as a genuine `MEDIUM` misconfiguration. Distinguishing "inconclusive" from "confirmed issue" would need a new field on the check result type and changes to every existing check, out of scope for Day 11.
- Scan execution model: resolved Day 12, asynchronous (background thread pool), not synchronous request-scoped, see FR-5.1 above and `ScanRunner`. Chosen because a worst case scan can take 20-30+ seconds, too long to hold an HTTP request open safely.
