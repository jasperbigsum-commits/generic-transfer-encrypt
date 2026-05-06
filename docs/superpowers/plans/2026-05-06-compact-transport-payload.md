
# Compact Transport Payload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship protocol V2 compact transport payloads that reduce GET/DELETE URL length without changing request methods, while keeping server-side backward compatibility with legacy payloads.

**Architecture:** Add dual-read protocol handling in Spring first, then switch SDK writers to compact wrapper keys plus compressed payloads. Keep response decoding backward compatible in every client while enabling compact response writing from the server.

**Tech Stack:** Java 8, Spring Boot 2, JUnit 5, vanilla JS, Vue 3 shared core, Flutter/Dart, gzip-compatible compression, Base64URL encoding.

---

### Task 1: Add Spring Protocol V2 Read/Write Support

**Files:**
- Modify: `spring2-plugin/src/main/java/io/github/jasper/transfer/encrypt/core/TransferConstants.java`
- Modify: `spring2-plugin/src/main/java/io/github/jasper/transfer/encrypt/model/TransferEnvelope.java`
- Modify: `spring2-plugin/src/main/java/io/github/jasper/transfer/encrypt/util/TransferJsonUtils.java`
- Modify: `spring2-plugin/src/main/java/io/github/jasper/transfer/encrypt/web/TransferEncryptionFilter.java`
- Modify: `spring2-plugin/src/main/java/io/github/jasper/transfer/encrypt/feign/TransferFeignClientWrapper.java`
- Test: `spring2-plugin/src/test/java/io/github/jasper/transfer/encrypt/TransferEncryptionIntegrationTest.java`

- [ ] Step 1: Write failing Spring integration tests for compact query, form, JSON, and compact encrypted response behavior.
- [ ] Step 2: Run the focused Spring test command and verify the new compact-protocol tests fail for the expected reasons.
- [ ] Step 3: Implement constants, dual-read parsing, compact wrapper decoding, compact response writing, and compact Feign query support with the minimal code needed to satisfy the tests.
- [ ] Step 4: Re-run the focused Spring test command and verify it passes.
- [ ] Step 5: Commit the Spring protocol V2 support changes.

### Task 2: Add Vanilla JS Compact Payload Encoding/Decoding

**Files:**
- Modify: `vanilla-js-plugin/transfer-encrypt.js`
- Modify: `vanilla-js-plugin/tests/transfer-encrypt.native.test.js`
- Create or Modify: `vanilla-js-plugin/vendor/*` only if a deterministic compression helper must be vendored

- [ ] Step 1: Write failing vanilla JS tests that assert GET URLs use `tp`, `ct`, `pv=2`, compact payload decoding works, and legacy response payloads still decode.
- [ ] Step 2: Run the focused vanilla JS test command and verify the compact-protocol assertions fail.
- [ ] Step 3: Implement compact wrapper writing, compressed payload encoding, and dual response decode support with the smallest change set that makes the tests pass.
- [ ] Step 4: Re-run the focused vanilla JS test command and verify it passes.
- [ ] Step 5: Commit the vanilla JS protocol V2 changes.

### Task 3: Align Vue 3 Shared Core with Protocol V2

**Files:**
- Modify: `vue3-plugin/transfer-encrypt-vue3-core.js`
- Modify: `vue3-plugin/tests/transfer-encrypt-vue3.native.test.mjs`

- [ ] Step 1: Write failing Vue core tests for compact wrapper generation and dual decode compatibility.
- [ ] Step 2: Run the focused Vue test command and verify the new tests fail.
- [ ] Step 3: Implement the same compact payload and compatibility behavior used in the vanilla JS core.
- [ ] Step 4: Re-run the focused Vue test command and verify it passes.
- [ ] Step 5: Commit the Vue 3 protocol V2 changes.

### Task 4: Align Flutter Protocol Implementation

**Files:**
- Modify: `flutter-plugin/lib/src/transfer_encrypt_protocol.dart`
- Modify: `flutter-plugin/lib/src/transfer_encrypt_client.dart`
- Modify: `flutter-plugin/lib/src/transfer_encrypt_dio_adapter.dart`
- Modify: `flutter-plugin/test/transfer_encrypt_client_test.dart`
- Modify: `flutter-plugin/test/transfer_encrypt_dio_adapter_test.dart`

- [ ] Step 1: Write failing Flutter tests for compact request payload generation and dual response decode compatibility.
- [ ] Step 2: Run the focused Flutter test command and verify the new protocol V2 tests fail.
- [ ] Step 3: Implement compact wrapper generation, compressed payload support, and legacy decode fallback in the Flutter protocol layer.
- [ ] Step 4: Re-run the focused Flutter test command and verify it passes.
- [ ] Step 5: Commit the Flutter protocol V2 changes.

### Task 5: Update Documentation and Run Full Verification

**Files:**
- Modify: `vanilla-js-plugin/README*` or relevant docs if compact protocol is documented there
- Modify: `vue3-plugin/docs/*` where request wrapper fields are described
- Modify: `flutter-plugin/docs/*` where request wrapper fields are described
- Modify: `README.md` if top-level protocol docs exist

- [ ] Step 1: Update protocol documentation to describe compact wrapper keys, version marker, compression, and compatibility behavior.
- [ ] Step 2: Run the full verification commands for Spring, vanilla JS, Vue, and Flutter.
- [ ] Step 3: Review the spec and confirm each requirement is implemented and tested.
- [ ] Step 4: Commit the documentation and verification adjustments.
