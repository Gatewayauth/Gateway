# Contributing to Gateway

Thanks for your interest in improving Gateway.

## How to contribute

- **Bugs & features:** open a [GitHub issue](https://github.com/Gatewayauth/Gateway/issues).
- **Changes:** open a pull request against `main`. PRs are the required path —
  `main` is branch-protected and every change goes through a PR.
- **Security vulnerabilities:** do **not** open a public issue — follow
  [SECURITY.md](SECURITY.md) (private GitHub Security Advisory).

## Development

```bash
./gradlew build        # compile + run the full test suite
./gradlew test         # tests only
./gradlew detekt       # static analysis / style
```

CI runs the build, tests, detekt, and the security scanners (CodeQL, Semgrep,
Trivy) on every push and PR. A PR must be green to merge.

## Coding standards

- Kotlin, targeting JVM 21. Follow the standard Kotlin/Java conventions
  (`kotlin.code.style=official`).
- Style and quality are enforced by **detekt** (`config/detekt/detekt.yml` plus
  the project's custom rules in `:detekt-rules`). Run `./gradlew detekt` before
  pushing.

## Testing policy

**New or changed functionality must ship with tests.** When you add major
functionality, add tests for it to the automated suite (JUnit / MockK; module
test suites live under each module's `src/test`). Perfection isn't required, but
untested new behaviour will be asked to add coverage before merge. Tests run in
CI via `./gradlew build`.

## Versioning & releases

Releases use [Semantic Versioning](https://semver.org) and are tagged in git
(`vX.Y.Z`), which triggers the container publish workflow and a GitHub Release
with human-readable notes.
