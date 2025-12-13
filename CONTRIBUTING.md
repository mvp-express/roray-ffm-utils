# Contributing to Roray FFM Utils

Thank you for your interest in contributing to Roray FFM Utils! This document covers development setup, build commands, and contribution guidelines.

## Development Setup

### Prerequisites

- **JDK 25+** (the project uses Java 25 toolchain)
- **Gradle 8.5+** (wrapper included)
- **Linux** recommended for full test coverage

### IDE Configuration

For IntelliJ IDEA:
1. Import as Gradle project
2. Set Project SDK to JDK 25
3. Enable annotation processing
4. Add VM options to run configurations:
   ```
   --enable-preview --enable-native-access=ALL-UNNAMED
   ```

For VS Code:
1. Install "Extension Pack for Java"
2. Configure `java.configuration.runtimes` to include JDK 25
3. Add launch configuration with VM args for preview features

---

## Gradle Commands

### Building

```bash
# Full build (compile + test + check)
./gradlew build

# Compile only (no tests)
./gradlew assemble

# Clean build artifacts
./gradlew clean

# Clean and rebuild
./gradlew clean build
```

### Testing

```bash
# Run all tests
./gradlew test

# Run tests with verbose output
./gradlew test --info

# Run a specific test class
./gradlew test --tests "express.mvp.roray.ffm.utils.memory.MemorySegmentPoolTest"

# Run a specific test method
./gradlew test --tests "express.mvp.roray.ffm.utils.memory.MemorySegmentPoolTest.testAcquireAndRelease"

# Run tests matching a pattern
./gradlew test --tests "*BinaryWriter*"

# Run tests and show standard output
./gradlew test --info --console=plain

# Continue running tests after failures
./gradlew test --continue

# Re-run tests (ignore up-to-date checks)
./gradlew test --rerun-tasks
```

### Code Quality

```bash
# Run Checkstyle
./gradlew checkstyleMain checkstyleTest

# Run SpotBugs (if configured)
./gradlew spotbugsMain

# Run all checks
./gradlew check
```

### Documentation

```bash
# Generate Javadoc
./gradlew javadoc

# Generate aggregated Javadoc (all subprojects)
./gradlew aggregateJavadoc

# Output location: build/docs/javadoc/
```

### Benchmarks

```bash
# Run JMH benchmarks (from benchmarks subproject)
./gradlew :benchmarks:jmh

# Run specific benchmark
./gradlew :benchmarks:jmh -Pjmh.includes="MemorySegmentPoolBenchmark"

# Run with specific iterations
./gradlew :benchmarks:jmh -Pjmh.warmupIterations=3 -Pjmh.iterations=5

# Run with profiler
./gradlew :benchmarks:jmh -Pjmh.profilers="gc"

# Available profilers: gc, stack, perf, async
./gradlew :benchmarks:jmh -Pjmh.profilers="async:output=flamegraph"

# Output location: benchmarks/build/results/jmh/
```

### Dependencies

```bash
# Show dependency tree
./gradlew dependencies

# Show dependencies for a specific configuration
./gradlew dependencies --configuration runtimeClasspath

# Check for dependency updates
./gradlew dependencyUpdates
```

### Publishing (Maintainers)

```bash
# Publish to local Maven repository
./gradlew publishToMavenLocal

# Publish to remote repository (requires credentials)
./gradlew publish
```

### Useful Combinations

```bash
# Quick validation before committing
./gradlew clean check

# Full CI build
./gradlew clean build javadoc

# Fast iteration during development
./gradlew assemble test --tests "*YourTest*"
```

---

## Common Issues

### FFM Access Errors

If you see `java.lang.IllegalCallerException`:
```
Add to your JVM args: --enable-native-access=ALL-UNNAMED
```

### Preview Feature Errors

If you see `preview features are not enabled`:
```
Add to your JVM args: --enable-preview
```

### Test Failures on Non-Linux

Some tests may require Linux-specific features. Use:
```bash
./gradlew test -x linuxOnlyTests
```

---

## Pull Request Process

1. **Fork** the repository and create a feature branch
2. **Write tests** for new functionality
3. **Run** `./gradlew clean check` before submitting
4. **Sign** the [CLA](CLA.md) if you haven't already
5. **Submit** PR with clear description of changes

### Commit Message Format

```
component: Short summary (50 chars or less)

Longer description if needed. Wrap at 72 characters.
Explain what and why, not how.

Fixes #123
```

### Code Style

- Follow existing code conventions
- Use meaningful variable names
- Add Javadoc for public APIs
- Keep methods focused and small
