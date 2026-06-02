# Generic Transfer Encrypt Spring 3 Plugin

Spring Boot 3 / Spring Framework 6 starter for the shared transport encryption protocol.

This starter contains only the Spring Boot 3 / `jakarta.servlet.*` adapter layer. Protocol, crypto, configuration, Feign wrapping, and servlet-neutral request/response processing live in:

- `generic-transfer-encrypt-core`

## Maven

```xml
<dependency>
    <groupId>io.github.jasperbigsum-commits</groupId>
    <artifactId>generic-transfer-encrypt-spring3-plugin</artifactId>
    <version>1.0</version>
</dependency>
```

## Runtime Matrix

- Spring Boot `3.2.x`
- Spring Framework `6.x`
- Spring Cloud `2023.x`
- Jakarta Servlet `6.x`
- Java `17+`

Use `spring2-plugin` for Spring Boot 2 / Spring Framework 5 applications.

## Auto Configuration

This module registers:

```text
io.github.jasper.transfer.encrypt.autoconfigure.TransferEncryptAutoConfiguration
```

through:

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## Configuration

The configuration shape is intentionally the same as `spring2-plugin` because both starters consume the same `TransferEncryptProperties` from `generic-transfer-encrypt-core`:

```yaml
transfer:
  encrypt:
    enabled: true
    private-key: "<server-sm2-private-key-hex>"
    public-key: "<server-sm2-public-key-hex>"
    include-path-regex:
      - "^/api/.*$"
    exclude-path-regex: []
    filter-order: 0
    multipart-md5-field-prefix: "__md5_"
    feign-enabled: true
    feign-public-keys:
      downstream-service: "<downstream-sm2-public-key-hex>"
```

The transport fields and multipart MD5 field names are unchanged from the Spring 2 starter.

## Verification

```powershell
mvn "-Dmaven.repo.local=.m2repo" -pl spring3-plugin -am test
```
