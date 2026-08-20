# Sentinel Monitoring Plan

**Status:** v1.0.0, written Day 19
**Scope:** what observability exists today, honestly, and what a real
deployment would need on top of it. This is a plan, not a claim that
monitoring is implemented, no live infrastructure exists to monitor yet
(see `docs/CLOUD_ARCHITECTURE.md` Section 1).

## 1. What exists today

- **Application logs.** Standard Spring Boot logging (INFO level by
  default), visible via `docker compose logs app` locally, or `kubectl
  logs` in a real cluster. Confirmed, by manual check on Day 4, to never
  contain a plaintext password, and structurally unable to leak a JWT
  through a stack trace, `server.error.include-stacktrace` is left at
  Spring Boot's own default (never included).
- **Health signal via `/v3/api-docs`.** Public and unauthenticated since
  Day 17, this is what `infra/k8s/deployment.yaml`'s readiness and
  liveness probes actually check today, a 200 response means the Spring
  context is up and the web layer is serving requests. It is not a true
  health check (it says nothing about whether the database connection
  pool is healthy, for instance), it is a real, already-existing
  endpoint reused pragmatically rather than a purpose-built one.
- **CI-level signal.** `.github/workflows/ci.yml`'s three jobs
  (build-and-test, CodeQL, Trivy) are a form of continuous monitoring
  in their own right: every push and PR is checked for a broken build,
  known-vulnerable dependencies, and common source-level security
  issues, and CodeQL/Trivy results are visible in the repository's
  Security tab.

## 2. What a real deployment would still need

Listed honestly as gaps, not glossed over, the same standard
`docs/THREAT_MODEL.md` already holds this project to.

### Actual health checks, not a borrowed endpoint
`/v3/api-docs` returning 200 proves the web layer is up, not that the
database is reachable. Adding `spring-boot-starter-actuator` with
`/actuator/health` (which does check the database connection via
Spring Boot's built-in `DataSourceHealthIndicator`) would be a small,
well-justified addition, deliberately not made today: it is a new
dependency, and per this project's own rule of never adding one
without a reason tied to an actual day's work, it belongs to whichever
day first needs it for something real, not bolted on here just because
this document mentions it.

### Metrics
No metrics are currently exported anywhere. A real deployment would
want, at minimum: request latency and error rate per endpoint, scan
duration (already implicitly knowable from `Scan.startedAt`/
`completedAt`, just not exported anywhere), and the `scanTaskExecutor`
thread pool's queue depth and active count from `AsyncConfig`, since a
full queue is this project's own documented Denial of Service
consideration (finding D-2 in `docs/THREAT_MODEL.md`). Micrometer plus
`actuator`'s Prometheus endpoint is the standard, idiomatic Spring Boot
path here, again not added today for the same reason as above.

### Centralized log aggregation
Locally and even in a single-cluster deployment, `kubectl logs` per pod
is workable. It stops being workable the moment there is more than one
replica actively serving traffic and something needs debugging across
all of them at once, which is already true today (`replicas: 2` in
`infra/k8s/deployment.yaml`). A real deployment would ship logs
somewhere queryable (CloudWatch Logs is the natural fit alongside the
RDS/ECR resources `infra/terraform` already provisions, since no
separate log aggregation account/service would be needed).

### Alerting
Nothing currently pages anyone about anything. The most valuable
alerts, in priority order, tied to this project's own documented risks:
a spike in `401`/`403` responses (credential stuffing, related to
finding D-1), the `scanTaskExecutor` queue approaching its 50-item
capacity (finding D-2), and RDS storage or connection count approaching
its limit. None of this exists today, and pretending otherwise would
misrepresent the project's actual maturity.

### An audit trail
This is really a monitoring/observability gap with a security
consequence, not just an operational one: finding R-1 in
`docs/THREAT_MODEL.md` already documents that there is no tamper-evident
record of who did what (registered a website, triggered a scan),
separate from ordinary application logs. A dedicated, append-only audit
log is the right fix, and belongs to a future day's actual
implementation work, not restated as new information here, this
section exists to connect that finding to the monitoring story rather
than let it live only in the threat model.

## 3. Why none of this blocks the Cloud Computing elective's scope

The build plan's own framing for Week 3 (see the wiki's Build Plan page)
is explicit that this week proves IaC and orchestration understanding
at a documentation grade, not a live, fully observable production
system. This plan demonstrates the same honesty this project has held
itself to since the threat model (Day 15): knowing exactly what is
missing, and why, is worth more here than a monitoring stack stood up
for its own sake with nothing real yet to monitor.
