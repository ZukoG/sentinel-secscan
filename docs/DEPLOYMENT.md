# Sentinel Deployment Guide

**Status:** v1.0.0, written Day 19
**Scope:** concrete steps for both deployment modes described in
`docs/CLOUD_ARCHITECTURE.md` Section 1, local (live, verified) and
cloud (documentation-grade, not applied). Local steps are re-verified
here as of Day 19 on a genuinely clean checkout, see Section 3.

## 1. Local deployment (verified, this is how the project has actually been run every day)

### Prerequisites
- Docker Desktop (with WSL2 backend on Windows)
- Nothing else. No local JDK, Maven, or Postgres install needed, the
  build and runtime both happen inside containers.

### Steps
```bash
git clone https://github.com/ZukoG/sentinel-secscan.git
cd sentinel-secscan
docker compose -f infra/docker-compose.yml up --build
```

That's the entire process, per NFR-7 in `docs/SRS.md`: no manual setup
beyond this command, no `.env` file required (`infra/.env.example`
documents the defaults already baked into
`infra/docker-compose.yml`, copy it to `infra/.env` only if you want
different local credentials).

What happens: Postgres starts first, `docker-compose.yml`'s healthcheck
gates the app container until `pg_isready` succeeds, then the app
starts, Flyway applies the schema
(`src/main/resources/db/migration`), and the API is reachable at
`http://localhost:8080`.

### Stopping
```bash
docker compose -f infra/docker-compose.yml down       # keep data
docker compose -f infra/docker-compose.yml down -v     # also wipe the Postgres volume
```

## 2. Cloud deployment (target, documentation-grade, not applied)

These steps describe what applying `infra/terraform/` and
`infra/k8s/` for real would involve. None of this has been run against
an actual AWS account, stated here as plainly as it is in
`infra/README.md`.

1. **Provision cloud resources.**
   ```bash
   cd infra/terraform
   terraform init
   TF_VAR_db_password="<a real password, never committed>" terraform plan
   TF_VAR_db_password="<same password>" terraform apply
   ```
   Produces an ECR repository and an RDS PostgreSQL instance (see
   `outputs.tf` for the exact values returned).

2. **Build and push the image.**
   ```bash
   docker build -f infra/docker/Dockerfile -t <ecr_repository_url>:latest .
   aws ecr get-login-password --region <region> | docker login --username AWS --password-stdin <ecr_repository_url>
   docker push <ecr_repository_url>:latest
   ```
   Not automated in CI yet, see `docs/CLOUD_ARCHITECTURE.md` Section 6.

3. **Create the Kubernetes Secret** (never committed, created directly
   against the cluster):
   ```bash
   kubectl create secret generic sentinel-secrets \
     --from-literal=SPRING_DATASOURCE_URL="jdbc:postgresql://<db_endpoint>/sentinel" \
     --from-literal=SPRING_DATASOURCE_USERNAME=sentinel \
     --from-literal=SPRING_DATASOURCE_PASSWORD="<the same password from step 1>" \
     --from-literal=JWT_SECRET="<a real, random production secret, never the application.properties dev default>"
   ```

4. **Substitute the image placeholder and apply the manifests.**
   `infra/k8s/deployment.yaml`'s `image: "<ECR_REPOSITORY_URL>:latest"`
   is a placeholder, substitute the real `ecr_repository_url` output
   from step 1 (via Kustomize or a simple `sed`, not by hand-editing
   the committed file), then:
   ```bash
   kubectl apply -f infra/k8s/deployment.yaml -f infra/k8s/service.yaml
   ```

5. **Not yet designed: external access.** `service.yaml` is `ClusterIP`
   only. Reaching the app from outside the cluster needs an Ingress or
   LoadBalancer Service with TLS termination, finding T-1 in
   `docs/THREAT_MODEL.md`, intentionally not invented here just to
   complete this list.

## 3. Environment variables reference

| Variable | Used by | Local default | Required in a real deployment |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | app | `jdbc:postgresql://localhost:5432/sentinel` | Yes, the RDS endpoint |
| `SPRING_DATASOURCE_USERNAME` | app | `sentinel` | Yes |
| `SPRING_DATASOURCE_PASSWORD` | app | `sentinel` | Yes |
| `JWT_SECRET` | app | dev-only fallback, see finding I-2 in `docs/THREAT_MODEL.md` | **Yes, must be overridden**, never the committed default |
| `JWT_EXPIRATION_MS` | app | `86400000` (24h) | Optional |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | local `db` container only | `sentinel` / `sentinel` / `sentinel` | N/A, RDS is provisioned differently, see `infra/terraform/variables.tf` |

## 4. Full end-to-end smoke test (Day 19)

Run start to finish on a genuinely clean stack (`docker compose down -v`
first, so no leftover data from any earlier day's testing), the last
check of core functionality before demo prep, per this day's own scope.

1. `docker compose -f infra/docker-compose.yml up --build` from a clean checkout.
2. `POST /api/auth/register` a new user.
3. `POST /api/auth/login`, receive a JWT.
4. `POST /api/websites` to register a website.
5. `POST /api/websites/{id}/scans` to trigger a scan.
6. Poll `GET /api/scans/{id}` until `status` is `COMPLETED`.
7. `GET /api/scans/{id}/report` and confirm a valid PDF comes back.

Result: **PASS**, no code changes needed. Real transcript, run against
`https://example.com` on a clean `docker compose up --build` after
`down -v`:

```
register                          -> 201
login                             -> 200, JWT received
add website                       -> 201 (id 1)
trigger scan                      -> 201, IN_PROGRESS (scan id 1, 22:07:35.770)
poll scan                         -> COMPLETED on the first poll, 22:07:39.777 (~4s)
                                      overallScore 50, riskRating HIGH
download report                   -> 200, application/pdf, Content-Length 1484,
                                      file starts with the real %PDF-1.6 magic bytes
```

Findings: `hsts` HIGH (missing `Strict-Transport-Security`),
`security-headers` HIGH (missing CSP, `Referrer-Policy`, and the other
two misconfigured), `https-usage`/`redirect-analysis`/`cookie-security`/
`ssl-certificate` all INFO. Score 50 matches `ScoringService`'s math
exactly (-25 for each HIGH finding, 100 start), and this is the same
result this exact target has produced consistently since Day 12,
confirming nothing regressed across 19 days of changes on top of the
same passive check logic.
