# Migration rollout policy

Every Flyway migration created after `V68` must include one of these comments in its first eight lines:

```sql
-- rollout: EXPAND
```

```sql
-- rollout: CONTRACT
```

`EXPAND` is the only policy allowed in the normal launch release path. It may add compatible schema, nullable columns, indexes, or new tables. Deploy compatible application code first, run backfills only after it is live, and retain legacy reads/writes until the contract release.

`CONTRACT` includes destructive work such as dropping or renaming schema. It is rejected by the normal release gate and requires a separate approved release after the retention window, a successful staging restore rehearsal, and a verified rollback/restore plan.

No migration may silently round, truncate, or rewrite production data. Data remediation must be explicit and verified in the rehearsal evidence.
