# Flyway schema policy

The default application configuration never baselines an existing schema
automatically. New databases run all migrations from `V1`.

For a database that already contains the four pre-rotation core tables:

1. Back up the database and compare its real schema with `V1`.
2. Stop application writes.
3. Run Flyway's explicit `baseline` command once with baseline version `1`.
4. Run `migrate`; `V2` adds and backfills the rotation fields and tables, and
   `V3` installs the circular current-assignment foreign key.
5. Start the application only after `validate` succeeds.

Do not set `baseline-on-migrate=true` in a long-lived application profile.
An explicit one-time baseline makes a schema mismatch visible instead of
silently accepting an unknown database.

Vendor-specific scripts live below `h2` and `mysql`. Spring Boot substitutes
the `{vendor}` segment in `spring.flyway.locations`, so only one vendor's
version stream is loaded for a datasource.
