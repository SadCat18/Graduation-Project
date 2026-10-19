# Flyway Failed Migration Recovery

Use this procedure only after a deployment fails during a Flyway migration.

1. Stop the application and take a database backup.
2. Read the failed statement in the application log and verify whether it made any schema changes.
3. Deploy the corrected migration artifact.
4. Run `flyway repair` against the same database to remove the failed history entry and refresh migration checksums.
5. Start the application again and confirm `flyway_schema_history` records the migration as successful.

For the V4 failure caused by `ADD COLUMN IF NOT EXISTS`, the database rejected the first statement during SQL parsing, so no V4 column was added. After deploying the corrected V4 migration, `flyway repair` is the required recovery step before restarting the application.

Do not delete `flyway_schema_history` rows manually unless a database administrator has verified the migration state and approved that recovery path.
