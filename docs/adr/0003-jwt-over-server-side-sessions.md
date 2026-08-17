# ADR 0003: Use JWT authentication instead of server-side sessions

## Status
Accepted (Day 4, formalized Day 16)

## Context
FR-1.3 in `docs/SRS.md` requires every endpoint except registration and
login to require valid credentials, and NFR-2 requires those credentials
to be signed and time limited. Two mainstream approaches fit: server-side
sessions (an opaque session identifier in a cookie, backed by a session
store such as Spring Session with Redis or a database table) or a signed,
stateless bearer token issued at login (JWT) that the client attaches to
every subsequent request.

## Decision
Use JWTs (jjwt 0.11.5, HS256 signing) issued by `JwtService` on
successful login, verified on every request by
`JwtAuthenticationFilter`, with `SecurityConfig` set to
`SessionCreationPolicy.STATELESS`, no server-side session state at all.
Tokens carry a 24 hour expiration
(`sentinel.jwt.expiration-ms`).

## Alternatives considered
- **Server-side sessions (Spring Session, backed by Redis or a database
  table).** A legitimate, well-understood approach, and it would allow
  instant server-side revocation (log a user out immediately by deleting
  their session), which JWTs cannot do without an additional blocklist.
  Rejected for this project's scope: it introduces a second stateful
  component (a shared session store) purely to support horizontal
  scaling, when the async scan execution model (`AsyncConfig`,
  Day 12) and the eventual containerized/k8s deployment story (Day 18)
  both favor a stateless API tier that any replica can serve without
  needing to see another replica's session data. For a project this
  size, that additional store is complexity without a matching benefit.
- **Third-party OAuth2/OIDC identity provider.** Would offload password
  storage and token issuance entirely. Rejected as disproportionate:
  Sentinel has its own local user accounts by design (FR-1.1/FR-1.2),
  there is no external identity federation requirement, and pulling in
  an external IdP would add an operational dependency this project does
  not otherwise need.

## Consequences
- The API tier is fully stateless: any request can be served by any
  instance without shared session storage, a direct fit for the
  containerized, potentially multi-replica deployment story planned for
  Week 3's infrastructure work.
- No CSRF token handling is needed (`csrf` is disabled in
  `SecurityConfig`), since there is no cookie-based session for a
  malicious page to ride on.
- A JWT cannot be revoked before it expires without adding a separate
  blocklist, which does not exist in this project. The accepted
  mitigation is the bounded 24 hour token lifetime, exposure from a
  compromised token is capped, not eliminated. This is a deliberate,
  documented tradeoff, not an oversight.
- The signing secret (`sentinel.jwt.secret`) becomes a single point of
  compromise: anyone holding it can forge a valid token for any user.
  This is already tracked as finding I-2 in `docs/THREAT_MODEL.md`
  (Day 15), which flags the dev-only default fallback currently visible
  in `application.properties` as a real gap for any non-local
  deployment.
