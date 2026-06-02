# Transfer Encrypt Core Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract shared transport encryption protocol code into one Maven module so Spring 2 and Spring 3 starters cannot drift.

**Architecture:** Add `transfer-encrypt-core` as a sibling jar module and a root aggregator POM. Keep servlet/MVC auto-configuration classes in each starter, but move protocol, crypto, model, config, Feign wrapping, JSON utilities, generic web helpers, and path matching into core.

**Tech Stack:** Maven multi-module aggregation, Java 8-compatible core, Spring Boot 2/3 starter modules, OpenFeign, Jackson, BouncyCastle.

---

### Task 1: Core Module

**Files:**
- Create: `pom.xml`
- Create: `transfer-encrypt-core/pom.xml`
- Create: `transfer-encrypt-core/src/main/java/io/github/jasper/transfer/encrypt/**`
- Create: `transfer-encrypt-core/src/test/java/io/github/jasper/transfer/encrypt/**`

- [x] Add a root aggregator POM with modules `transfer-encrypt-core`, `spring2-plugin`, and `spring3-plugin`.
- [x] Create `transfer-encrypt-core` with Java 8 source/target, BouncyCastle, Jackson, Spring core/context-compatible dependencies, optional Feign, and tests.
- [x] Move common source packages into core: `annotation`, `core`, `crypto`, `model`, `feign`, `config/TransferEncryptProperties`, `util/TransferJsonUtils`, `util/TransferWebUtils`, `web/TransferPathMatcher`.
- [x] Keep `TransferWebUtils` free of servlet imports.
- [x] Move servlet-neutral request/response exchange handling into `TransferWebExchangeProcessor`.

### Task 2: Starter Adapters

**Files:**
- Modify: `spring2-plugin/pom.xml`
- Modify: `spring2-plugin/src/main/java/io/github/jasper/transfer/encrypt/**`
- Modify: `spring3-plugin/pom.xml`
- Modify: `spring3-plugin/src/main/java/io/github/jasper/transfer/encrypt/**`

- [x] Add a dependency from each starter to `transfer-encrypt-core`.
- [x] Remove duplicated common source files from each starter.
- [x] Add starter-local servlet utility methods for `readBody` and `extractParameters`.
- [x] Keep Spring 2 servlet classes on `javax.servlet.*`.
- [x] Keep Spring 3 servlet classes on `jakarta.servlet.*`.

### Task 3: Verification

**Files:**
- Modify: `spring2-plugin/README.md`
- Modify: `spring3-plugin/README.md`

- [x] Run `mvn "-Dmaven.repo.local=.m2repo" test` from the repository root.
- [x] Run `mvn "-Dmaven.repo.local=.m2repo" -pl spring2-plugin -am test`.
- [x] Run `mvn "-Dmaven.repo.local=.m2repo" -pl spring3-plugin -am test`.
- [x] Update READMEs to document that both starters depend on `generic-transfer-encrypt-core`.
