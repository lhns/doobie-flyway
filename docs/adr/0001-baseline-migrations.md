# 1. Baseline migrations

Status: accepted

## Context

A long-lived schema accumulates versioned migrations without bound. Provisioning a fresh
database means replaying all of them, which is slow, and it keeps every piece of dead DDL —
tables that were later dropped, columns that were later renamed, workarounds for database
versions nobody runs any more — alive and executable forever. The natural fix is to squash
the history: replace the first N migrations with a single snapshot that produces the same
schema.

Flyway cannot do this on its own. Its `baseline` command only *marks* a version as already
applied, so that migrations below it are never run against a database that was created some
other way. It does not provision anything, so it cannot bootstrap an empty database. Flyway
does have a real baseline-migration feature (`baselineMigrationPrefix`, default `B`), but it
is a paid Teams feature.

What we want:

- a fresh database applies one snapshot and then continues with the migrations after it;
- a database already in the field is completely unaffected, whether it was created from a
  snapshot or predates snapshots entirely;
- no fork of Flyway, and no dependency on a paid edition.

## Decision

Introduce a parallel migration prefix `B`. A file `Bnnn__description.sql` is a full schema
snapshot equivalent to applying every `Vnnn` migration up to and including version `nnn`.

`BaselineMigrations.withBaselineMigrate` wraps the configured `ResourceProvider` and rewrites
the resource list before Flyway ever sees it. Flyway is never taught about baselines; it is
simply handed a different set of migrations. It is given the *migration info of the target
database* up front, which is what lets the rewrite depend on the database's own history.

The rewrite, per call to `getResources(prefix, suffixes)`:

1. Take the script name of the **first** applied migration from `info.applied()`, or none if
   the schema history is empty. (`applied()` excludes Flyway's own bookkeeping rows, so this
   is genuinely the first real migration.)
2. Ask the delegate provider for resources with an **empty** prefix — deliberately
   unfiltered, since asking for `V` would drop the `B` resources before they can be renamed.
   Bucket them with `(\D+)(\d+)__.*` into prefix and integer version. Names that do not match —
   repeatable `R__` migrations, anything else — go into a passthrough bucket.
3. Select the effective baseline:
   - **empty history** → the highest-numbered `B`;
   - **non-empty history** → the `B` whose path matches the first applied script, or **none**
     if nothing matches.
4. Emit, in order: the selected baseline with its filename rewritten from `Bnnn` to `Vnnn`;
   every `V` whose version is strictly greater than `nnn`; and the passthrough bucket. With
   no baseline selected, every `V` is emitted unchanged.
5. Filter by the `prefix` that was actually requested. The renamed baseline passes as a `V`,
   which is what makes Flyway execute it and record it in the schema history.

Only the *filename* is rewritten in step 4. The relative path is left alone, so the schema
history records the baseline under its real `B` name — which is precisely what step 3 reads
back on the next run.

### Why the selection rule has two arms

This is the entire safety argument, and each of the three resulting lifecycles has a test in
`BaselineMigrationsSuite`:

- **Fresh database.** History is empty, so the newest snapshot wins. The database jumps
  straight to it and skips the versions it covers. This is the case the whole feature exists
  for.

- **Database bootstrapped from a baseline.** Its first applied script is `B002__snapshot.sql`,
  so step 3 re-selects `B002` *specifically*, even once a newer `B004` has been added. The
  recorded version and checksum keep matching the resource Flyway resolves, so validation
  stays clean and the database is never asked to re-baseline. Selecting the newest baseline
  here instead would invalidate the history of every deployed database — a plausible-looking
  "idempotency fix" that must not be applied.

- **Database predating baselines.** Its first applied script is a plain `V001__…`, which
  matches no baseline, so step 3 selects none and step 4 emits the full versioned history.
  Baselines are invisible to it, permanently.

### Matching the recorded script

Flyway records a migration's script as its path relative to the configured location. For the
flat layout (`classpath:db/migration` with the files directly inside) that equals the bare
filename, and the original implementation compared against `getFilename()` alone. For a
nested layout the recorded script is `nested/B002__snapshot.sql`, the comparison fails, and
the database silently falls into the third arm above — replaying a history it has already
partly applied, which surfaces as a validation failure.

The comparison therefore accepts the relative path *or* the bare filename, with or without a
leading directory component. Both are accepted so that databases migrated by either version
of this library keep resolving to the same baseline.

## Consequences

- A `Bnnn` snapshot must stay byte-stable once any database has been bootstrapped from it —
  its checksum is in that database's schema history. Fixing a mistake in a published snapshot
  means adding a new one, not editing the old one.
- `B` and `V` share one version space. A snapshot must be generated at a real `V` version
  boundary, and `Bnnn` and `Vnnn` describe the same point in history.
- Versions are parsed as plain integers, so this does not support Flyway's dotted version
  numbers (`V1.2__…`). Such names fall into the passthrough bucket.
- Only the *first* applied migration is consulted. Manually truncating a schema history
  changes which arm a database takes.
- `withBaselineMigrate` needs `info()` from the unmodified configuration, which is a round
  trip to the database before migrating. Validating migration naming has to be switched on
  *after* the rewrite, since beforehand the resource list still contains `B` files.
- Building the default `ResourceProvider` requires `org.flywaydb.core.internal.scanner.Scanner`,
  as Flyway exposes no public API for it. This is the library's one dependency on Flyway
  internals and is confined to `ResourceProviders.orDefault`; that method is the single place
  to adapt if a future Flyway release moves it.
