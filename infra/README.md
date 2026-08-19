# Infrastructure

## Local development
[docker-compose.yml](docker-compose.yml) and [docker/Dockerfile](docker)
are the actively used, fully verified local dev setup: `docker compose up
--build` from a clean checkout, no manual steps beyond the documented
environment variables (NFR-7 in `docs/SRS.md`). Every day of this
project's work has been verified against this stack, see `CLAUDE.md`'s
day-by-day log for specifics.

## Terraform (`terraform/`)
Baseline, **documentation-grade** configuration proving IaC understanding
for the Cloud Computing elective, **not applied against a live AWS
account**. `terraform validate` passes locally (Terraform 1.15.9, AWS
provider ~> 5.0), but nothing here has been planned or applied for real,
there is no live infrastructure behind it and no cost being incurred.

It provisions exactly what this project's own architecture actually
needs, not a generic template: an ECR repository for the image
`docker/Dockerfile` already builds, and an RDS PostgreSQL instance
matching the `postgres:16-alpine` engine already used locally. See
`terraform/variables.tf` for what's configurable and
`terraform/outputs.tf` for what a real apply would hand back (the ECR
URL, the RDS endpoint).

Deliberately out of scope for this baseline: a VPC built from scratch
(a real deployment would either reuse an existing one or add this as a
follow-up), and an EKS cluster (provisioning one from scratch is a
large amount of additional state, more than a documentation-grade
baseline needs to prove the point; the Kubernetes manifests below are
written to apply to any cluster, not necessarily one Terraform itself
provisioned).

To actually try it against a real account: `terraform init`, then
`terraform plan` with `TF_VAR_db_password` set (never committed, no
default on purpose, see the comment on that variable).

## Kubernetes (`k8s/`)
Baseline Deployment and Service manifests for running the application
on any Kubernetes cluster. Also **not applied against a live cluster**,
verified instead with `terraform fmt`/`validate`-equivalent checks for
YAML: parsed and structurally checked with PyYAML, `kubectl`'s own
dry-run needs a reachable API server, which no cluster here provides.

- `deployment.yaml`: 2 replicas (a direct, deliberate consequence of
  [ADR 0003](../docs/adr/0003-jwt-over-server-side-sessions.md), the
  API is fully stateless, so this costs nothing to be true), readiness
  and liveness probes against `/v3/api-docs` (public, unauthenticated,
  Day 17, no extra dependency like Spring Actuator needed just to have
  something to probe), and secrets sourced from a `sentinel-secrets`
  Kubernetes Secret that is **not created or committed here**, the same
  "no secrets committed to the repo" rule `application.properties`
  already follows for `sentinel.jwt.secret`. This is also the concrete
  mitigation path for finding I-2 in `docs/THREAT_MODEL.md`: a real
  deployment supplies `JWT_SECRET` via that Secret, it never falls back
  to the dev-only default baked into `application.properties`.
- `service.yaml`: internal `ClusterIP` only. No Ingress or
  LoadBalancer, external exposure and TLS termination don't exist yet
  anywhere in this project (finding T-1 in `docs/THREAT_MODEL.md`),
  and adding one just to have something externally reachable here would
  bolt on a real, unfinished security decision rather than solving it
  properly on its own day.
- The Deployment's image field is a placeholder
  (`<ECR_REPOSITORY_URL>:latest`). A real deployment pipeline
  substitutes the real `ecr_repository_url` Terraform output plus an
  actual tag (via Kustomize or CI templating), not a hand-edited
  commit.

## What's still missing for a real deployment
Tracked honestly rather than implied as done: a provisioned EKS (or
equivalent) cluster to actually run the Deployment above on, the
`sentinel-secrets` Secret itself, an Ingress/load balancer with TLS
termination, and a CI/CD step that builds, pushes to ECR, and applies
the manifests. Day 19's deployment guide is where the full picture
(what exists, what's still a gap, and why) gets written up properly.
