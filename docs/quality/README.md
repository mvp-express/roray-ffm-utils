# Quality Harness

Required checks:

```bash
./tools/harness/check.sh
./gradlew check
```

Current harness coverage:
- required docs exist
- expected Gradle modules are present
- expected package roots are present
- `AGENTS.md` maps to durable docs

Future checks:
- native platform smoke checks
- allocation regression probes
- benchmark freshness checks
