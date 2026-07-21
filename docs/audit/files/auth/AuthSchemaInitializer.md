# AuthSchemaInitializer

`src/main/java/com/jilali/auth/AuthSchemaInitializer.java`

## Purpose
A startup hook that creates the `auth_session` table by executing the classpath `schema.sql` on application boot. It is a lightweight substitute for a migration framework (Flyway/Liquibase), justified by there being a single small idempotent `CREATE TABLE IF NOT EXISTS`.

## Responsibilities
- Listen for Micronaut's `StartupEvent`.
- Load `schema.sql` from the classpath (fail hard if missing).
- Strip `--` line comments, split the script on `;`, and execute each non-blank statement over a raw JDBC `Statement`.
- Log "Auth schema ready" or throw `IllegalStateException` on failure.

## Public API
- `AuthSchemaInitializer(DataSource dataSource)` — constructor injection of the JDBC datasource.
- `void onStartup(StartupEvent event)` — package-private `@EventListener`; runs the schema.
- Private static `String withoutComments(String sql)` — strips `--` comments before splitting.

## Dependencies
- Injects: `javax.sql.DataSource`.
- Micronaut `StartupEvent`/`@EventListener`, SLF4J, JDBC.
- Reads classpath resource `schema.sql` (defines `auth_session`).
- Depended on BY: nothing directly — it is a `@Singleton` framework lifecycle bean. The table it creates is used by `JdbcAuthSessionRepository`.

## Coupling and cohesion analysis
High cohesion (one job: initialize schema). Coupling is minimal and appropriate (`DataSource` is an abstraction). The naive SQL-splitting is the only fragility. This is essentially a hand-rolled micro-migration runner.

## Code smells
- Mild Primitive Obsession / fragile parsing: `withoutComments(...).split(";")` is a hand-rolled SQL tokenizer. A `;` inside a string literal or a `$$`-quoted block would break it. Acceptable for the current trivial schema, but a latent trap if `schema.sql` grows.

## Technical debt
- No real migration tooling — schema evolution (adding columns, indexes, backfills) has no versioning story. The moment the schema needs to change in a non-additive way, this approach fails.
- The whole script runs on every startup; there is no record of what has been applied.

## Duplicate logic
- None within this package. This is the only schema-execution code here.

## Dead or unused code
- None. `onStartup` is framework-invoked via `@EventListener` — not dead despite having no explicit caller.

## Refactoring recommendations
- If the schema ever grows beyond one table, adopt Flyway/Liquibase (Micronaut has first-class support) and delete this class.
- If keeping it, at minimum replace the naive `split(";")` with a comment-and-string-aware statement splitter, or require one statement per file.
