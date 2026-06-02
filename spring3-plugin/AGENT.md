# AGENT.md

## 1. Module Scope

`spring3-plugin` is the Spring Boot 3 / Spring Framework 6 server starter for the shared transport encryption protocol.

Common protocol, crypto, configuration, Feign wrapping, and servlet-neutral HTTP exchange logic live in `../transfer-encrypt-core`. Keep this module focused on the Spring Boot 3 / `jakarta.servlet.*` adapter layer.

It must keep protocol behavior compatible with `spring2-plugin` while using:

- Spring Boot `3.2.x`
- Spring Framework `6.x`
- `jakarta.servlet.*`
- Java `17`

## 2. Compatibility Rules

- Keep protocol fields unchanged: `transferPayload`, `originalContentType`, `encryptedKey`, `encryptedData`, `contentMd5`, `timestamp`.
- Keep multipart MD5 field names unchanged: `__md5_<fieldName>` and `__md5_<fieldName>__<index>`.
- Keep configuration prefix unchanged: `transfer.encrypt`.
- Keep the filter registration bean name unchanged: `transferEncryptionFilterRegistrationBean`.
- Register auto-configuration through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Do not add `javax.servlet.*` imports to this module.
- Do not copy common protocol logic back from `transfer-encrypt-core`.

## 3. Verification

Run:

```powershell
mvn "-Dmaven.repo.local=.m2repo" -pl spring3-plugin -am test
```
