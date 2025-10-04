# Spring Boot 3.2 → 3.5.6 Upgrade Checklist

This playbook guides the Universal Reconciliation Platform backend through consecutive upgrades from Spring Boot 3.2.x to 3.5.6. Follow each stage sequentially, committing at every milestone so you can bisect regressions easily.

**Latest run snapshot (2025-10-04):** Upgraded directly from Spring Boot 3.2.5 to 3.5.6 on Java 17. All backend, automation, and seed suites passed without code changes beyond the parent version bump. Virtual threads remain disabled automatically on Java 17, and the default `ZIP` layout produced the same executable JAR. No additional configuration tweaks were required for Actuator, Security, or data sources.

## References
- [Spring Boot 3.3 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.3-Release-Notes)
- [Spring Boot 3.4 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes)
- [Spring Boot 3.5 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes)
- [Spring Framework 6.2 Upgrade Guide](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.2-Release-Notes)
- [Spring Boot Supported Java Versions](https://docs.spring.io/spring-boot/docs/current/reference/html/getting-started.html#getting-started.system-requirements)
- [Spring Security 6.5 Migration](https://docs.spring.io/spring-security/reference/servlet/appendix/migration/index.html)

## Before you start
- [ ] Create a dedicated branch in `backend/` and confirm the working tree is clean (`git status`).
- [ ] Capture the current build status: `./mvnw -B verify` (or the repo’s backend gate) and note baseline failures.
- [ ] Export dependency snapshots (`./mvnw -q dependency:tree -Dincludes=org.springframework`) to compare post-upgrade drifts.
- [ ] Audit third-party starters (Spring LDAP, OAuth2, jjwt, Flyway, database drivers) for compatibility with Spring Boot 3.5.
- [ ] Confirm CI workers can run with Java 21 (Spring Boot 3.3+ supports Java 17+ but the new build plugins leverage JDK 21 features).

## Environment prerequisites
- [ ] Upgrade local JDK to at least 21 (use `sdk use java 21.0.x` or the team-approved toolchain). Spring Boot 3.5 still supports Java 17, but the Gradle/Maven plugins require JDK 21 for native image and test slices.
- [ ] Update Maven to ≥ 3.9.4 (already satisfied in current toolchain) and ensure the Maven wrapper is refreshed (`./mvnw -v`).
- [ ] Review Dockerfiles or container build images; plan an update if they pin older JDK runtimes.

## Stage 0 – Refresh Spring Boot 3.2.x baseline
- [ ] Bump the parent `spring-boot-starter-parent` to the latest 3.2 patch (3.2.11 at the time of writing) and re-run tests to ensure a clean baseline before major upgrades.
- [ ] Execute `./mvnw clean verify` and regression scripts (integration harness, seed scripts) to confirm the baseline remains green; commit as “Spring Boot 3.2.x baseline refresh”.

## Stage 1 – Upgrade to Spring Boot 3.3.x
- [ ] Update the parent version to `3.3.6` (latest patch) and refresh the Maven wrapper (`./mvnw -N wrapper:wrapper -Dmaven=3.9.9` if required).
- [ ] Address configuration changes:
  - Replace deprecated management endpoints configuration if using `management.endpoints.web.exposure.include=*` (3.3 restricts wildcard usage).
  - Update Actuator metrics tags to opt in via `management.metrics.distribution.percentiles-histogram`.
  - Opt in to the new BCrypt defaults or pin the legacy strength (`spring.security.password.encoder.strength`).
- [ ] Replace `SpringApplication.setBannerMode` usages with the builder API (`SpringApplicationBuilder` or `SpringApplication.setBannerMode(Banner.Mode.OFF)` after creation).
- [ ] Update `@ConfigurationProperties` binding classes if constructor binding is used—Spring Boot 3.3 enforces explicit annotations.
- [ ] Re-run `./mvnw clean verify`. Investigate test slices failing due to Actuator/Security defaults (Spring Security 6.3).
- [ ] Run automation suites that depend on backend APIs (integration harness, regression tests) to surface behavioural regressions.
- [ ] Commit with notes about config and dependency adjustments (“Upgrade backend to Spring Boot 3.3.x”).

## Stage 2 – Upgrade to Spring Boot 3.4.x
- [ ] Update the parent to `3.4.2`.
- [ ] Ensure Jakarta EE dependencies align (Spring Boot 3.4 upgrades to Jakarta Validation 3.1 and Servlet 6.1).
- [ ] Adjust configuration for the new Logback defaults; confirm custom appenders still initialise.
- [ ] For Testcontainers, upgrade to ≥ 1.20 to align with JDK 21 module changes (if we rely on live containers).
- [ ] Migrate any use of `RestTemplateBuilder#rootUri` chaining to the new `UriBuilder`. Spring Boot 3.4 deprecates some legacy builder semantics.
- [ ] Run `./mvnw clean verify` and all scripted gates. Pay attention to LDAP integration tests; Spring Security 6.4 tightened defaults.
- [ ] Commit with migration notes (“Upgrade backend to Spring Boot 3.4.x”).

## Stage 3 – Upgrade to Spring Boot 3.5.6
- [ ] Update the parent version to `3.5.6`.
- [ ] Update `spring-boot-maven-plugin` executions if we opt into the new build info layout (`<layout>ZIP</layout>` default changed). Explicitly set `<layout>ZIP</layout>` if we need to retain legacy behaviour.
- [ ] Adopt the new `spring.threads.virtual.enabled` default (now `true` for MVC). Decide whether to enable virtual threads globally or pin to `false` for compatibility.
- [ ] Migrate any `RestClient` usages to the new stable APIs; the old incubator variants were removed.
- [ ] Re-evaluate Actuator health group configuration: `.status.http-mapping` moved under `management.endpoint.health.status.http-mapping`.
- [ ] Update Docker/packaging scripts to use JDK 21+ runtime layers if not already done.
- [ ] Run full quality gates: backend unit/integration tests, frontend tests, Playwright, integration harness, `local-dev` scripts, historical seeding.
- [ ] Capture key metrics (startup time, health endpoint) for regression comparison.
- [ ] Commit as “Upgrade backend to Spring Boot 3.5.6”.

## Post-upgrade hardening
- [ ] Regenerate dependency reports and document new transitive versions (Spring Framework 6.2.1, Spring Security 6.5, Spring Data 2024.1).
- [ ] Monitor production-like environments for new warnings (virtual threads, Tomcat 10.2 behaviours).
- [ ] Update CI/CD runner images to ensure `JAVA_HOME` points to 21 when building containers.
- [ ] Remove temporary compatibility flags once the application stabilises (e.g., `spring.security.filter.dispatcher-types`, old Actuator exposure settings).

## Lessons learned (append as you go)
- Java 17 still works with Spring Boot 3.5.6; virtual threads stay off without setting `spring.threads.virtual.enabled=false`. Revisit once the runtime moves to Java 21.
- The `spring-boot-maven-plugin` retained the legacy `ZIP` layout by default; no reconfiguration was required for Docker or the integration harness.
- Existing LDAP, OAuth2 resource server, and JPA configurations continued to initialize without property changes—still re-run smoke/regression suites to confirm behavioural parity.
