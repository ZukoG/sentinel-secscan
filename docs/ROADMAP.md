# Sentinel Roadmap

**Status:** v1.0.0, written Day 20
**Scope:** what v1.0.0 actually ships, and what would come next. Every
item below already has its full reasoning written up elsewhere in this
repo, this document is a prioritized index into that work, not a
restatement of it.

## v1.0.0 (this release)

A passive web security assessment platform: register a website,
trigger a scan across six passive checks, get a scored risk rating and
a downloadable PDF report, track score trends over time. JWT auth,
per-user data isolation, async scan execution, a generated OpenAPI spec,
CI with SAST and dependency scanning, a STRIDE threat model, four
Architecture Decision Records, and baseline (documentation-grade)
Terraform and Kubernetes manifests. See the project wiki's
[Build Plan](https://github.com/ZukoG/sentinel-secscan/wiki/Build-Plan)
for the full day-by-day breakdown of how it was built.

## v1.1 candidates, in priority order

Priority follows severity where a finding already has one
(`docs/THREAT_MODEL.md`), otherwise follows what unblocks the most
other work.

1. **SSRF protection in the scanner engine** (finding I-4, HIGH). Resolve
   a registered URL's hostname before scanning and reject
   private/loopback/link-local ranges, re-checked per redirect hop. The
   highest-severity unmitigated finding in the threat model, and the
   only one rated HIGH that touches live code rather than
   infrastructure.
2. **Fail-fast JWT secret** (finding I-2, MEDIUM). Refuse to start
   outside a dev profile if `JWT_SECRET` is unset, rather than silently
   falling back to the committed dev default. Small, contained,
   `SecurityConfig`/`JwtService`-scoped.
3. **Apply the Terraform for real** (`docs/CLOUD_ARCHITECTURE.md`
   Section 1). Everything in `infra/terraform/` validates today but has
   never been pointed at a real AWS account. Doing so is the
   prerequisite for every other cloud-deployment item below.
4. **TLS termination** (finding T-1, MEDIUM today, would be HIGH on any
   real deployment). An Ingress or load balancer in front of the
   Kubernetes Service, the dotted line in
   `docs/CLOUD_ARCHITECTURE.md`'s target diagram. Depends on item 3.
5. **Auth rate limiting** (finding D-1, MEDIUM). A per-IP rate limit on
   `/api/auth/register` and `/api/auth/login`.
6. **A CI/CD deploy stage** (`docs/CLOUD_ARCHITECTURE.md` Section 6).
   Build, push to ECR, apply the Kubernetes manifests, on merge to
   `main`. Needs item 3 done first, no ECR repository exists to push to
   otherwise.
7. **Domain ownership verification** (finding S-3, MEDIUM). A DNS TXT
   record challenge before a website can be scanned, closing the one
   remaining accepted risk from `docs/SRS.md` section 2.6.
8. **Actual health checks and metrics** (`docs/MONITORING.md`).
   `spring-boot-starter-actuator` for a real `/actuator/health` that
   checks the database connection, plus Micrometer/Prometheus metrics,
   the `scanTaskExecutor` queue depth in particular (ties to finding
   D-2).
9. **An audit trail** (finding R-1, LOW). A dedicated, append-only log
   of security-relevant actions, separate from ordinary application
   logs.
10. **Re-scan throttling** (finding D-4, LOW). A minimum interval
    between scans of the same website.

## Explicitly not on this roadmap

- **Active or offensive scanning of any kind.** Not a v1 limitation,
  not a future feature, a permanent constraint (NFR-1 in `docs/SRS.md`).
  Nothing above changes this.
- **A frontend.** The original tech stack list scoped a React frontend
  as "added later, not day one priority." It was never started, the
  generated Swagger UI (`docs/adr` aside, see Day 17) has served as the
  practical way to exercise the API throughout this project. Worth
  reconsidering only if a real, non-technical demo audience needs one,
  not because a REST API alone is somehow incomplete.
