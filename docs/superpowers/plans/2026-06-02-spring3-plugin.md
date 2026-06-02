# Spring 3 Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independent Spring Boot 3 starter module that preserves the existing transport encryption protocol and Spring 2 starter behavior.

**Architecture:** Create `spring3-plugin` as a sibling module to `spring2-plugin`. Port the Spring MVC adapter to `jakarta.servlet.*`, keep the package name and public API stable, and register auto-configuration through Boot 3 `AutoConfiguration.imports`.

**Tech Stack:** Maven, Java 17 source/target for Boot 3 tests, Spring Boot 3.2.x, Spring Cloud 2023.x OpenFeign, Jakarta Servlet 6, JUnit 5.

---

### Task 1: Spring 3 Module Skeleton

**Files:**
- Create: `spring3-plugin/pom.xml`
- Create: `spring3-plugin/AGENT.md`
- Create: `spring3-plugin/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] Create a Maven module with artifact `generic-transfer-encrypt-spring3-plugin`, Boot 3 dependency management, Spring Cloud 2023 dependency management, `jakarta.servlet-api`, `spring-webmvc`, `spring-boot-autoconfigure`, Jackson, BouncyCastle, optional Feign, and test dependencies.
- [ ] Register `io.github.jasper.transfer.encrypt.autoconfigure.TransferEncryptAutoConfiguration` in Boot 3 `AutoConfiguration.imports`.

### Task 2: Failing Spring 3 Tests

**Files:**
- Create: `spring3-plugin/src/test/java/io/github/jasper/transfer/encrypt/TransferEncryptionIntegrationTest.java`
- Create: `spring3-plugin/src/test/java/io/github/jasper/transfer/encrypt/TransferEncryptionFilterRegistrationIntegrationTest.java`
- Create: `spring3-plugin/src/test/java/io/github/jasper/transfer/encrypt/TransferEncryptionFilterOrderIntegrationTest.java`
- Create: `spring3-plugin/src/test/java/io/github/jasper/transfer/encrypt/TransferEncryptionFeignChainIntegrationTest.java`
- Create: `spring3-plugin/src/test/java/io/github/jasper/transfer/encrypt/Sm2TestKeySupport.java`
- Create: `spring3-plugin/src/test/java/io/github/jasper/transfer/encrypt/web/TransferEncryptionFilterTest.java`

- [ ] Copy the existing Spring 2 behavior tests into `spring3-plugin`.
- [ ] Replace test servlet imports with `jakarta.servlet.*`.
- [ ] Run `mvn "-Dmaven.repo.local=.m2repo" test` from `spring3-plugin`.
- [ ] Expected RED: tests cannot compile or run because Spring 3 production classes do not exist yet.

### Task 3: Port Production Code

**Files:**
- Create: `spring3-plugin/src/main/java/io/github/jasper/transfer/encrypt/**`

- [ ] Copy Spring 2 production sources into `spring3-plugin`.
- [ ] Replace `javax.servlet.*` production imports with `jakarta.servlet.*`.
- [ ] Change auto-configuration annotation from `@Configuration` to Boot 3 `@AutoConfiguration`.
- [ ] Keep config keys, protocol fields, bean names, Feign annotation, and package names unchanged.

### Task 4: Verify and Document

**Files:**
- Create: `spring3-plugin/README.md`

- [ ] Run `mvn "-Dmaven.repo.local=.m2repo" test` from `spring3-plugin`.
- [ ] If dependency resolution needs network access, rerun the same Maven command with approval.
- [ ] Add a short README showing Maven coordinates, Boot 3 target, and the unchanged `transfer.encrypt.*` configuration shape.
- [ ] Run `mvn "-Dmaven.repo.local=.m2repo" test-compile` from `spring2-plugin` to ensure the existing Spring 2 module was not broken.
