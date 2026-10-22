# ADR-0005: Secrets are typed, logs are redacted, backups are off

Status: Accepted (Phase 0)

## Decision
1. Every sensitive field (UUIDs, passwords, tokens, REALITY keys, original share
   links) is a `Secret` whose `toString()` is `[REDACTED]`.
2. Free text that might contain such data passes through `Redactor.redact()`;
   proxy links and subscription URLs are redacted entirely.
3. Error and diagnostic strings are built from enum codes and redacted text;
   models carry `safeMessage`/`safeDetail` fields only.
4. `android:allowBackup="false"`.
5. Exports contain credentials by design; the UI warns, exporters never log.
6. Tests assert that fixture secrets never appear in messages.

## Consequences
+ Accidental string interpolation of a model cannot leak credentials.
- `Secret.reveal()` call sites must be reviewed.
- `Redactor` is a heuristic safety net, not a guarantee.
