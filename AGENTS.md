# Repository Agent Instructions

## Project Structure

- The root `pom.xml` is the Maven parent and aggregates `search-cli` and `search-ws`.
- `search-cli` retrieves, assembles, and indexes DINA documents.
- `search-ws` provides the REST API over the managed Elasticsearch cluster.
- `es-init-container` and `local` contain Elasticsearch initialization and local deployment assets.
- `docs` contains design notes for search and document assembly behavior.

## Working In This Repository

- Follow the existing Java package structure and module-local patterns. Keep changes in the module that owns the behavior unless a shared contract or parent configuration is involved.
- Treat REST responses, Elasticsearch query behavior, index mappings, and document assembly as external contracts. Update focused tests and relevant docs or fixtures when changing those contracts.
- Avoid editing generated `target/` content. Prefer source resources and tests.
- Keep dependency and Maven plugin changes in the root parent unless a module-specific need is clear.

## Build And Test

- Check the active JDK against the root POM before diagnosing Java compilation errors. The root POM, CI workflow, and module READMEs currently specify Java 25.
- Compile the reactor with `mvn clean compile`.
- Run focused module tests with `mvn -pl search-ws -am test` or `mvn -pl search-cli -am test`.
- Integration tests may require Docker/Testcontainers. Use `mvn verify` when integration coverage is relevant and Docker is available.
- CI also runs `mvn checkstyle:check` and `mvn spotbugs:check`; use these when changes affect style or static analysis.

## Diagnosing Failures

- Start from the first actionable error in the build output. Check the active Java and Maven versions, the failing module, and whether the failure occurs during compilation or testing before changing code.
- Prefer the narrowest command that reproduces the failure, then rerun that same check after a fix.
- Do not alter CI, toolchain versions, Elasticsearch configuration, or existing suppressions merely to make a local build pass; establish the owning configuration and intended compatibility first.