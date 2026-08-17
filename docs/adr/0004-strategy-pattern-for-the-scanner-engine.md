# ADR 0004: Use the Strategy pattern for the scanner engine's checks

## Status
Accepted (Day 7, formalized Day 16)

## Context
NFR-4 in `docs/SRS.md` requires that adding a new passive check must
not require modifying `ScannerEngine` or any existing check. By Day 9
there were six independent checks (HTTPS usage, security headers, SSL
certificate, HSTS, cookie security, redirect analysis), each inspecting
one aspect of a website's observable configuration, each fully
self-contained, none needing to know about, coordinate with, or run
after any other. Whatever orchestrates them needed to treat "run every
registered check against this website" as an open-ended operation, not
a fixed, hardcoded sequence.

## Decision
Define a `ScanCheck` interface with a single `run` method returning a
`CheckResult`. Each check is a small Spring-managed `@Component`
implementing that interface. `ScannerEngine` takes a constructor-injected
`List<ScanCheck>`, Spring collects every bean implementing the interface
into that list automatically, and iterates over it, running each check
and wrapping it individually in a `try`/`catch` that converts a thrown
exception into a `CheckResult` (severity `MEDIUM`) rather than letting
one failing check abort the scan or crash the others (this is also how
NFR-6, resilience, is satisfied, at the engine level, so every future
check inherits it for free rather than needing to reimplement it).

## Alternatives considered
- **One monolithic orchestrating method** calling each check's logic
  inline in sequence (e.g. a single `ScannerService` with six private
  methods). Rejected outright: this is exactly what NFR-4 rules out,
  every new check would mean editing that method directly, violating
  the open/closed principle the requirement exists to enforce.
- **Chain of Responsibility.** Considered briefly since a sequence of
  checks can resemble a pipeline. Rejected because Chain of
  Responsibility's defining trait, each link decides whether to handle a
  request or pass it to the next, doesn't match this problem: every
  check always runs, none of them decide whether to hand off to another,
  and none needs to see another's result. Strategy's "one interface,
  many interchangeable, independent implementations, an engine that
  runs all of them" is a closer fit than a pass-along chain.
- **Visitor pattern.** Would add double dispatch (a check "visiting" a
  website) for no real benefit here, there is only one domain type being
  inspected (`Website`), not a hierarchy of varying types each check
  needs to handle differently. Unjustified complexity for this shape of
  problem.

## Consequences
- Adding a new check is one new `@Component` implementing `ScanCheck`;
  nothing else in the codebase changes, `ScannerEngine`'s constructor
  injection picks it up automatically the next time the application
  context starts.
- Every check's return type is a `CheckResult` record, not the `Finding`
  JPA entity, a deliberate deviation from the original class diagram:
  no `Scan` exists yet at check-execution time (that happens at
  orchestration, Day 12), so checks return a plain, persistence-free
  value that `ScanService` later converts into real `Finding` rows.
- `ScannerEngine` never needs to know how many checks exist or what any
  of them individually do, it only needs the list and the shared
  try/catch wrapper, keeping it genuinely closed for modification as new
  checks are added.
