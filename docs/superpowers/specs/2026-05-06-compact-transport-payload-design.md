# Compact Transport Payload Design

## Background

The current transport-encryption protocol preserves HTTP method semantics, but `GET` and `DELETE` requests place the full encrypted wrapper into the query string. In the browser clients this happens by appending `transferPayload` plus `originalContentType` to the URL.

That design is functional, but the resulting URL can become very large because:

- `encryptedData` grows with business payload size
- SM4 ciphertext is currently represented as hex text
- the inner envelope is serialized with verbose field names
- the serialized envelope is wrapped again as Base64URL text

When the URL crosses limits in browsers, gateways, or servlet containers, the encrypted wrapper may be truncated or rewritten. Downstream this can surface as SM2 decryption failures such as `invalid point coordinates`, even though the actual root cause is a damaged transport payload.

## Goal

Reduce transport payload length substantially while keeping the original request method unchanged.

## Non-Goals

- Do not change `GET` to `POST`
- Do not remove SM2/SM4 or MD5 verification
- Do not require a big-bang cutover across all clients and servers

## Recommended Approach

Introduce a compact protocol variant with:

1. Short field names for both outer wrapper and inner envelope
2. Compressed `transferPayload` content before Base64URL encoding
3. Backward-compatible server-side decoding for both legacy and compact formats

## Protocol V2

### Outer Wrapper

Legacy wrapper:

```json
{
  "transferPayload": "...",
  "originalContentType": "application/json"
}
```

Compact wrapper:

```json
{
  "tp": "...",
  "ct": "j"
}
```

`ct` values:

- `j` = `application/json`
- `f` = `application/x-www-form-urlencoded`
- fallback: keep the original string when no enum mapping exists

### Inner Envelope

Legacy envelope:

```json
{
  "encryptedKey": "...",
  "encryptedData": "...",
  "contentMd5": "...",
  "timestamp": 1710000000000
}
```

Compact envelope:

```json
{
  "k": "...",
  "d": "...",
  "m": "...",
  "t": 1710000000000
}
```

## Encoding Flow

### Legacy Flow

`envelope object -> JSON -> Base64URL -> transferPayload`

### Compact Flow

`envelope object with short keys -> minified JSON -> compress -> Base64URL -> tp`

Compression applies to the serialized inner envelope only. The outer wrapper remains query-safe plain parameters.

## Compression Choice

Use gzip-compatible compression on both client and server.

Reasons:

- widely available in Java and browser/runtime ecosystems
- predictable interoperability for JS, Vue, Flutter, and Spring
- significant gain on repetitive hex-heavy JSON payloads

The compact payload should carry an explicit protocol marker. Recommended format:

- outer wrapper adds `pv=2`
- server interprets `pv=2` as “compact keys + compressed payload”

Example:

```text
tp=<base64url(gzip(minified-json))>&ct=j&pv=2
```

## Compatibility Strategy

### Server Read Path

Server decoding must support both versions:

1. If `tp` exists, use compact wrapper rules
2. Else if `transferPayload` exists, use legacy wrapper rules
3. If `pv=2`, Base64URL decode then decompress
4. If no `pv`, keep current Base64URL decode path
5. When reading the envelope, accept both `k/d/m/t` and legacy field names

### Client Write Path

Browser and other client SDKs should emit compact protocol by default after upgrade.

### Response Path

Responses should also move to the compact wrapper for consistency, while server decoders keep reading both old and new formats.

## Implementation Scope

### Spring

- extend constants to include compact field names and protocol version markers
- teach request decoding to read both legacy and compact wrappers
- add compressed payload decode/encode helpers
- support compact response writing
- keep existing behavior for legacy requests

### Vanilla JS

- generate compact wrapper for query, form, and JSON requests
- compress the inner envelope before Base64URL encoding
- decode both legacy and compact responses during transition

### Vue 3

- mirror the vanilla JS protocol changes in the shared core

### Flutter

- align request/response protocol implementation with compact wrapper and payload compression

## Risks

### Compression Runtime Support

Browser gzip/deflate support is not uniform if implemented only with built-in Web APIs. We should use a small deterministic compression helper in JS clients rather than rely on environment-specific behavior.

### Partial Rollout

If a new client talks to an old server without compatibility support, requests will fail immediately. Server-side dual-read support should land before or together with client rollout.

### Debuggability

Compressed payloads are less human-readable in logs. To keep incidents diagnosable, server logs should include:

- wrapper version
- compact payload length
- decoded envelope key lengths

without logging plaintext or secret material.

## Testing Plan

### Spring Tests

- legacy request decode still works
- compact request decode works for query, form, and JSON
- legacy response decode compatibility remains intact
- compact response encoding works
- malformed compressed payload fails with clear transport exceptions

### Vanilla JS Tests

- `GET` URL contains `tp`, `ct`, and `pv=2`
- compact payload round-trip works
- legacy response payload can still be decoded
- compact response payload can be decoded

### Vue 3 Tests

- same protocol assertions as vanilla core

### Flutter Tests

- request compact wrapper generation
- response dual-read compatibility
- compact payload round-trip

## Rollout Order

1. Add Spring dual-read and dual-write capability
2. Ship compact protocol in browser and Flutter SDKs
3. Keep dual-read support on the server during transition
4. Optionally remove legacy write path later, but keep legacy read path longer

## Recommendation

Proceed with protocol V2 using:

- `tp/ct/pv` outer wrapper
- `k/d/m/t` inner envelope
- compressed inner payload before Base64URL encoding
- server-side dual-read compatibility

This gives the largest URL reduction without changing the original request method semantics.
