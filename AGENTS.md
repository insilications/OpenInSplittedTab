# Repository Guidelines

## Project Structure & Module Organization
- Source: `src/main/kotlin/org/insilications/openinsplitted/` (Kotlin actions and core logic).
- Plugin config: `src/main/resources/META-INF/plugin.xml` (actions, IDs).
- Build: `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`.
- CI: `.github/workflows/build.yml` (build, verify, artifact upload).
- Docs: `README.md`.
- Changelog: `CHANGELOG.md`.

## Build And Development Commands
- `./gradlew build` — Compiles and packages; runs checks.
- `./gradlew verifyPlugin` — Runs IntelliJ Plugin Verifier against configured IDEs.
- `./gradlew dependencyUpdates` — Reports available dependency upgrades.
- `./gradlew buildPlugin` — Produces a zip under `build/distributions/`.

## Coding Style & Naming Conventions
- Language: Kotlin 2.2 targeting JVM 21 (Gradle toolchain configured).
- Style: IntelliJ default Kotlin style; 4-space indent; no wildcard imports.
- Names: `UpperCamelCase` for classes (e.g., `OpenInSplittedTabAction`), `lowerCamelCase` for methods/vars, `SCREAMING_SNAKE_CASE` for constants.
- Keep APIs public.

## Testing Guidelines
- Adding tests is not currently necessary.

## Commit & Pull Request Guidelines
- Commits: concise present-tense messages. Recommended format: `feat(action): add new split mode`.

## Security & Configuration Tips
- Do not commit signing materials. CI/Release uses env vars: `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`.
- Respect `pluginSinceBuild` in `gradle.properties` when upgrading platform versions.
- The project uses Gradle's version catalog to specify dependencies: `gradle/libs.versions.toml`.
- Update `<actions>` element in `plugin.xml` when actions/IDs change.
