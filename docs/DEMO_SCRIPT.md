# Sentinel Demo Script

**Status:** v1.0.0, written Day 20, for use on Day 21.
**Target length:** 6 to 8 minutes. Reviewers are grading a working demo,
not a finished enterprise product, see the project wiki's
[Build Plan](https://github.com/ZukoG/sentinel-secscan/wiki/Build-Plan).
This script prioritizes showing real, working functionality first, the
engineering rigor behind it second.

## 0. Before recording
```bash
docker compose -f infra/docker-compose.yml down -v   # clean slate
docker compose -f infra/docker-compose.yml up --build
```
Have `http://localhost:8080/swagger-ui/index.html` open in a browser
tab, and a terminal ready for `curl`. Both are shown, Swagger UI for
anyone unfamiliar with reading raw HTTP, `curl` for anyone who wants to
see exactly what's happening on the wire.

## 1. Intro (30 seconds)
> "Sentinel is a passive web security assessment platform. You give it
> a URL, it checks HTTPS usage, security headers, SSL certificate
> status, HSTS, cookie flags, and redirect behavior, the same way a
> browser visit would, no active attacks, no exploitation, ever, that's
> a permanent design constraint, not a v1 limitation. It scores what it
> finds, and generates a PDF report."

## 2. Register and log in (45 seconds)
Show `POST /api/auth/register`, then `POST /api/auth/login` returning a
JWT. Point out the password never reappears anywhere, and login returns
the same generic error for a wrong password and a nonexistent email.

## 3. Register a website (30 seconds)
`POST /api/websites` with a real URL (`https://example.com` is the one
used throughout this project's own testing). Point out URL validation:
malformed or non-http(s) URLs get rejected with a 400.

## 4. Trigger and watch a scan (90 seconds, the core of the demo)
`POST /api/websites/{id}/scans`, point out it returns immediately with
`IN_PROGRESS`, the scan runs asynchronously so the request doesn't
block. Poll `GET /api/scans/{id}` a couple of times, show the status
flip to `COMPLETED`, then walk through the actual findings: which
checks passed, which flagged something, the severity of each, and the
resulting score and risk rating.

## 5. Download the PDF report (30 seconds)
`GET /api/scans/{id}/report`, open the downloaded PDF on screen. Point
out it's generated fresh from the same data just shown, not a separate
system that could drift out of sync.

## 6. History and trend (30 seconds)
Trigger a second scan against the same site, show `GET
/api/websites/{id}/scans` (history) and `.../trend` (the score
comparison between the two most recent completed scans).

## 7. Cross-user isolation, briefly (20 seconds)
Register a second user, show that the first user's website/scan returns
`404` for the second user, not `403`. One line of narration is enough:
"a resource that exists but isn't yours looks identical to one that
doesn't exist at all."

## 8. The engineering behind it (90 seconds)
This is where "working demo" becomes "working demo backed by real
rigor," shown quickly, not dwelled on:
- **GitHub Actions**, a green run: build-and-test, CodeQL, and Trivy, all
  passing on the latest merge.
- **`docs/THREAT_MODEL.md`**, scroll past the STRIDE table, point out at
  least one honestly-documented gap (the SSRF finding, I-4) rather than
  only showing what's already solved.
- **`docs/adr/`**, one ADR opened briefly (0004, the Strategy pattern
  for the scanner engine) as the strongest "design pattern with a real
  reason" story.
- **Swagger UI**, `/swagger-ui/index.html`, the Authorize button, a
  request made straight from the browser.

## 9. Wrap-up (20 seconds)
> "That's Sentinel end to end: register, scan, score, report, all
> passive, all tested, all documented. `docs/ROADMAP.md` has what's
> next, SSRF protection in the scanner engine is the top item."

## Fallback if something breaks live
Every step above has already been run and captured as real output in
`docs/DEPLOYMENT.md`'s Day 19 smoke test section. If live `curl`/Swagger
UI calls fail for an environment reason during recording (network,
Docker Desktop state), narrate over that document's actual transcript
instead of re-attempting live, it is real, previously-captured output,
not a hypothetical one.
