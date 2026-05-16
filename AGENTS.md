# AGENTS.md

## Purpose

Agent harness for `/home/ubuntu/mvp-express/roray-ffm-utils`.

Repository knowledge map: `docs/INDEX.md`.

Workflow guidance: `docs/WORKFLOW.md`.

## Rules

- Treat source and build files as authoritative; docs may be stale.
- Preserve low-allocation FFM, pool, queue, and off-heap collection behavior.
- Prefer public API tests before implementation changes.
- Be explicit about native/platform assumptions.
- Preserve existing local dirt unless the user asks to touch it.

## Verification

Run:

```bash
./tools/harness/check.sh
./gradlew check
```
