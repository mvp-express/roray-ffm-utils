# Workflow Guidance

## Start

1. Read `AGENTS.md`.
2. Use `docs/INDEX.md`.
3. Check `git status --short`.
4. Validate docs against source and Gradle modules.

## Implementation

- Add or update one public API behavior test first.
- Preserve buffer ownership, lifetime, and close/release semantics.
- Keep platform and native-call assumptions explicit in docs and tests.
- Run focused tests first, then full `./gradlew check`.

## Validation

```bash
./tools/harness/check.sh
./gradlew check
```
