# Existing local data and account ownership

A new Neon database starts empty. The Android and web clients both read the selected Spring Boot server's database; neither copies the PC database. Choose either a fresh cloud database or an explicit, reviewed migration of your own data.

The account update adds `reality_users`, `auth_sessions`, and nullable `activities.owner_id`. Existing activities receive no owner automatically. An account's activity list and all session, break, progress, streak, and report reads require matching ownership. A guessed foreign or unowned ID returns 404. Sessions and breaks inherit access through their parent activity; they do not require a second manual owner assignment.

Do not assign every legacy activity to whoever registers first. If old rows belong to different people, identify the correct owner of each activity before making an assignment. If ownership is unknown, leave the rows unowned and inaccessible.

## Fresh deployment

Create the empty Neon project, deploy the cloud profile, and register through the UI. New activities are assigned to the authenticated user automatically. No migration or ownership SQL is required.

## Deliberate migration of your PC records

1. Stop or pause writes to the source application while making a consistent export. Keep a backup before applying the account schema or transferring records.
2. Use PostgreSQL `pg_dump`/`pg_restore` from an appropriate PostgreSQL client installation, with credentials supplied privately. Never commit the export; it contains personal records and may contain password hashes or authentication tokens if exporting the new schema.
3. Restore only into the intended Neon database. `pg_restore --clean` is destructive; do not use it against a database with other users' data. A fresh separate database avoids overwriting cloud records.
4. Start the updated backend to initialize the added schema, then register the intended owner account through the application. Obtain its numeric user ID from `/api/auth/me` while signed in, or from an administrator's database query.
5. In the provider's SQL editor, inspect the legacy activity IDs and the target account. Assign only an explicitly reviewed set of activity IDs. The following SQL is a template; replace the IDs with your own verified values before running it:

   ```sql
   -- Read first. Never select or copy password hashes/authentication tokens.
   SELECT id, username, display_name FROM reality_users ORDER BY id;
   SELECT id, name, owner_id FROM activities ORDER BY id;

   BEGIN;
   -- Example IDs only: review activity IDs 101 and 102 and owner ID 7 yourself.
   UPDATE activities
   SET owner_id = 7
   WHERE id IN (101, 102) AND owner_id IS NULL;
   SELECT id, name, owner_id FROM activities WHERE id IN (101, 102);
   -- Use COMMIT only after checking the affected IDs and ownership.
   ROLLBACK;
   ```

   The example deliberately ends with `ROLLBACK`. After reviewing the actual IDs and expected affected row count, an administrator can rerun the transaction with `COMMIT`. No application code executes this assignment.

6. Sign in as the owner and verify activities, sessions, breaks, and reports. Sign in as another account and verify that it cannot see them. Keep the original backup until those checks pass.

Local `LocalDateTime` records have no timezone offset. The Docker server is `Asia/Kolkata`, matching the Android default and the original PC setup. If source records were created in a different server timezone, assess that difference before importing; changing the display setting alone does not convert stored timestamps.

The current application does not provide account deletion, password reset, bulk import, ownership transfer, or hard deletion of activities through REST. These migration steps are administrator operations, not hidden UI features. Activity deletion remains a soft deactivation; session deletion deletes its break records first.

For independent backups, export the Neon database periodically to storage you control. Free provider retention and restore windows should not be your only copy of important data.
