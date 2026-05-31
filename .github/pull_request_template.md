## EXPLAIN PLAN Checklist (ADR-010)

For every new or modified MongoDB query in this PR:

- [ ] Ran `explain()` — `winningPlan.stage` is `IXSCAN` (not `COLLSCAN`)
- [ ] `tenantId` is the first field in every compound index (Annex B)
- [ ] Partial index considered if active documents represent < 20% of the collection

_If this PR contains no new or modified MongoDB queries, check all three boxes and add "N/A — no MongoDB queries changed"._

---

## Changes

<!-- Describe what was changed and why. -->

---

## Test Coverage

<!-- List tests added or modified. Confirm unit-tests, integration, bdd-tests, and contract-tests jobs pass locally or in CI. -->

- [ ] Unit tests updated / added
- [ ] Integration tests updated / added (if adapter code changed)
- [ ] BDD scenarios updated / added (if user-facing behaviour changed)
- [ ] Contract tests updated / added (if API contract changed)
