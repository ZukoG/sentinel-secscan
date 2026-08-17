# ADR 0002: Use PostgreSQL as the primary data store

## Status
Accepted (Day 1, formalized Day 16)

## Context
Sentinel's domain is inherently relational: a `User` owns zero or more
`Website` rows, each `Website` has zero or more `Scan` rows, and each
`Scan` produces zero or more `Finding` rows, all connected by real
foreign keys with genuine referential integrity needs (a `Finding`
without a `Scan`, or a `Scan` without a `Website`, is meaningless data).
Ownership scoping, the security pattern used throughout the API
(`findByOwnerId`, `findByIdAndOwnerId`, and similar), also relies on
straightforward relational joins and indexed foreign key lookups rather
than duplicated or denormalized data.

The stack was set at project scaffolding (Day 1): Spring Boot, Spring
Data JPA, Hibernate, and Flyway for schema migrations. Any relational
database compatible with that combination was a candidate.

## Decision
Use PostgreSQL (16, alpine image in Docker) as the only data store, with
Flyway owning the schema (`ddl-auto=validate`, migrations are the single
source of truth for table structure, see `V1__init_schema.sql`).

## Alternatives considered
- **MySQL/MariaDB.** A reasonable, equally relational alternative.
  Not chosen because PostgreSQL has a stronger reputation for standards
  compliance and strict data integrity (its constraint and transaction
  handling in particular), and the official `postgres:16-alpine` Docker
  image is small and well maintained, a direct fit for the containerized
  local dev setup built on Day 3. No feature this project needs (window
  functions, JSON columns, arrays) tips the balance either way at this
  scale, this came down to reliability reputation and Docker image
  quality rather than a hard technical requirement.
- **H2 (embedded/in-memory).** Would remove the need for a database
  container entirely, and is the usual default for quick Spring Boot
  demos. Rejected deliberately: every test in this project already runs
  against a real Postgres instance rather than H2 or Testcontainers
  (see the testing approach used from Day 2 onward), because a schema
  or dialect difference between H2 and the real production database is
  exactly the kind of bug that shouldn't be caught for the first time in
  production. Using H2 anywhere, even just locally, would undermine that
  guarantee.
- **A document store (e.g. MongoDB).** Rejected outright. The domain is
  a small number of entities with real foreign key relationships and
  ownership-scoped queries, not documents that vary in shape or need to
  be queried by arbitrary nested fields. A schemaless store would fight
  this data model rather than help it, and would lose Flyway's
  versioned, reviewable schema history, which has already caught real
  issues in this project's history (e.g. confirming no migration was
  needed when `Scan.riskRating` changed from `String` to an enum on Day
  11, since the existing `VARCHAR` column already fit).

## Consequences
- Every environment, local dev, CI, and (eventually) production, runs
  against the same database engine, eliminating an entire class of
  "worked in dev, broke in prod" dialect bugs.
- Flyway migrations are the single, versioned source of truth for the
  schema; `ddl-auto=validate` means Hibernate can never silently drift
  the schema out from under them.
- Local development and CI both require a running Postgres instance
  (via Docker Compose locally, a `postgres:16-alpine` service container
  in GitHub Actions), there is no lighter-weight fallback. This has been
  an accepted, working tradeoff since Day 3.
