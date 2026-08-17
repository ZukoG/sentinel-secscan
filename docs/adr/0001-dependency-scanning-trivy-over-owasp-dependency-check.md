# ADR 0001: Use Trivy instead of OWASP Dependency-Check for CI dependency scanning

## Status
Accepted (Day 10)

## Context
The build plan's Day 10 ("CI hardening: SAST + dependency scanning") names
`OWASP Dependency-Check` explicitly as the dependency vulnerability
scanner to add to `.github/workflows/ci.yml`, alongside a commit message
of `ci: add SAST and OWASP dependency-check stages`.

`dependency-check-maven`, the actual Maven plugin, queries the National
Vulnerability Database (NVD) for known CVEs against the project's
dependencies. As of the NVD's current API policy, running it without a
registered API key is severely rate limited, slow enough to make a CI
job impractical (potentially many minutes to hours per run, depending on
NVD load). A usable setup needs a free NVD API key, requested by
registering an account at https://nvd.nist.gov/developers/request-an-api-key,
then stored as a GitHub Actions repository secret.

Registering that account is a manual, standalone step outside this
repository (an NVD account plus a stored GitHub Actions secret), not
something CI setup alone can complete.

## Decision
Use [Trivy](https://github.com/aquasecurity/trivy) instead, via
[aquasecurity/trivy-action](https://github.com/aquasecurity/trivy-action),
scanning the built Spring Boot fat jar (`target/*.jar`) rather than a bare
`pom.xml`. Scanning the built artifact means dependencies bundled
transitively under `BOOT-INF/lib` are visible to the scanner, not just
the ones declared directly in `pom.xml`.

Findings upload to the repository's Security tab as SARIF (via
`github/codeql-action/upload-sarif`) but do not fail the build. No
baseline or triage has been done yet on this codebase's existing
dependencies, hard-failing CI on the first day a scanner is introduced
would turn every future PR red for reasons unrelated to what it actually
changed. Whether to enforce a fail-on-critical policy is a decision to
revisit once there's a real finding to weigh it against.

## Alternatives considered
- **OWASP Dependency-Check.** Matches the plan's literal wording and is a
  legitimate, widely used tool. Rejected only for the practical reason
  above: it needs a registered NVD API key to run at a usable speed, and
  running without one defeats the point of having it in CI at all.
  Revisiting this later is entirely reasonable, see below.
- **GitHub Dependabot** (native alerts, `.github/dependabot.yml`).
  Free, zero CI runtime cost, and arguably the most "GitHub-native" fit.
  Not chosen for this ADR because it's a repository-level feature rather
  than a visible CI pipeline stage, and the plan's Day 10 goal was
  specifically to add a build-time gate, not just enable a dashboard
  setting. Nothing here rules out enabling Dependabot alerts
  additionally later; it's complementary, not exclusive, with either
  choice above.

## Consequences
- No account setup required to get dependency scanning working today.
- Trivy scans the actual shipped artifact (including transitive
  dependencies), which is arguably more accurate than a bare `pom.xml`
  scan would be.
- This project's dependency scanning uses a different tool than the one
  literally named in the original day-by-day build plan. That plan is
  a personal, local planning document (not part of this repository), so
  this ADR is the durable record of why, for anyone reading the repo on
  its own.
- If a free NVD API key is obtained later, adding OWASP Dependency-Check
  as an additional, complementary job (not a replacement) would be a
  reasonable, low-risk follow-up, most real-world pipelines run more
  than one scanner rather than relying on a single tool's coverage.
