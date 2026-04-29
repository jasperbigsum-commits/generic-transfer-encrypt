# AGENT.md

## 1. 模块定位

`spring2-plugin` 是本仓库中面向 Spring Boot 2 / Spring MVC 的服务端传输加密 starter。

它负责：

- 入站请求解密
- 出站响应加密
- 文件上传/下载 MD5 完整性校验
- 选择性 OpenFeign 出站加密
- 通过自动配置方式无侵入接入业务服务

这不是 Spring Boot 3 模块，也不是通用 Servlet 抽象层。这里的代码必须继续围绕：

- Spring Boot `2.7.x`
- Spring Framework `5.x`
- `javax.servlet.*`
- Java `8`

## 2. 关键入口

启动与装配入口：

- `src/main/java/io/github/jasper/transfer/encrypt/autoconfigure/TransferEncryptAutoConfiguration.java`
- `src/main/resources/META-INF/spring.factories`

核心协议与加密：

- `src/main/java/io/github/jasper/transfer/encrypt/core/TransferEnvelopeCodec.java`
- `src/main/java/io/github/jasper/transfer/encrypt/core/TransferConstants.java`
- `src/main/java/io/github/jasper/transfer/encrypt/crypto/TransferCryptoService.java`
- `src/main/java/io/github/jasper/transfer/encrypt/crypto/DefaultTransferCryptoService.java`

Web 入站/出站链路：

- `src/main/java/io/github/jasper/transfer/encrypt/web/TransferEncryptionFilter.java`
- `src/main/java/io/github/jasper/transfer/encrypt/web/TransferHttpServletRequestWrapper.java`
- `src/main/java/io/github/jasper/transfer/encrypt/web/TransferMultipartIntegrityInterceptor.java`
- `src/main/java/io/github/jasper/transfer/encrypt/web/TransferPathMatcher.java`

配置与 MVC 挂载：

- `src/main/java/io/github/jasper/transfer/encrypt/config/TransferEncryptProperties.java`
- `src/main/java/io/github/jasper/transfer/encrypt/config/TransferEncryptWebMvcConfigurer.java`

Feign 接管链路：

- `src/main/java/io/github/jasper/transfer/encrypt/annotation/TransferEncryptedFeignClient.java`
- `src/main/java/io/github/jasper/transfer/encrypt/feign/TransferFeignBeanPostProcessor.java`
- `src/main/java/io/github/jasper/transfer/encrypt/feign/TransferFeignClientWrapper.java`
- `src/main/java/io/github/jasper/transfer/encrypt/feign/TransferFeignInvocationContext.java`

## 3. 必须守住的行为

### 协议字段

除非明确做跨端协议升级，否则不要修改这些字段名：

- `transferPayload`
- `originalContentType`
- `encryptedKey`
- `encryptedData`
- `contentMd5`
- `timestamp`
- `X-Transfer-Content-MD5`

### Web 层行为

`TransferEncryptionFilter` 当前承担以下稳定行为：

- 只对命中路径规则的请求生效
- 自动解密 `JSON`、`form-urlencoded`、`GET/DELETE query`
- 不破坏 `@RequestBody`、`@RequestParam`、`@ModelAttribute` 原有绑定
- 对二进制/文件响应附加 MD5，而不是加密文件内容
- 把 `TransferRequestContext` 放入请求属性，供后续链路使用

### Multipart 规则

文件上传校验字段规则必须保持兼容：

- 单文件：`__md5_<fieldName>`
- 多文件：`__md5_<fieldName>__<index>`

默认前缀来自 `transfer.encrypt.multipart-md5-field-prefix`，默认值是 `__md5_`。

### Feign 行为

Feign 不是全量强制加密，当前规则必须保持：

- 只有显式标注 `@TransferEncryptedFeignClient` 的 Feign 接口/方法才进入加密链路
- 未标注的 Feign 调用保持原样
- `publicKeyAlias` 优先级高于 host/serviceId 映射
- `md5Enabled` 只控制额外 MD5 头校验，不影响信封内部 `contentMd5`

## 4. 自动配置约束

这个 starter 依赖“低侵入自动装配”，以下点不要随意破坏：

- 入口仍通过 `spring.factories` 暴露
- 主自动配置类是 `TransferEncryptAutoConfiguration`
- `transfer.encrypt.enabled` 默认开启
- `transfer.encrypt.feign-enabled` 默认开启
- Filter 注册 Bean 名称是 `transferEncryptionFilterRegistrationBean`
- Filter order 来自 `transfer.encrypt.filter-order`

尤其注意：

- 不要把 `@ConditionalOnMissingBean` 改得过宽，避免业务侧存在别的 `FilterRegistrationBean` 时导致本 Filter 丢失
- 不要随意改 `transferEncryptionFilterRegistrationBean` 这个 Bean 名称，已有测试依赖它

## 5. 实现约束

1. 保持 Java 8 兼容，不要引入更高版本 API。
2. 保持 `javax.servlet.*`，不要引入 `jakarta.servlet.*`。
3. 不要把 Spring Boot 3 的 `AutoConfiguration.imports` 方案直接替换掉当前 `spring.factories`。
4. 不要重新引入 Lombok。
5. 优先保持已有 `ObjectMapper`、`FilterRegistrationBean`、Feign 接管方式不变，除非明确修复缺陷。
6. 任何影响协议的改动，都应假设会波及根仓库里的 JS / Vue / Flutter / E2E 模块。

## 6. 常见改动定位

- 请求体解密、响应加密异常：先看 `web/TransferEncryptionFilter.java`
- query/form 参数绑定异常：先看 `web/TransferHttpServletRequestWrapper.java` 与 `util/TransferWebUtils.java`
- 路径命中异常：先看 `web/TransferPathMatcher.java` 与 `config/TransferEncryptProperties.java`
- 上传 MD5 校验异常：先看 `web/TransferMultipartIntegrityInterceptor.java`
- 信封编码、SM2/SM4、MD5 逻辑异常：先看 `core/TransferEnvelopeCodec.java` 与 `crypto/`
- Feign 没有被接管或接管过度：先看 `feign/TransferFeignBeanPostProcessor.java`
- Feign 请求/响应信封处理异常：先看 `feign/TransferFeignClientWrapper.java`
- 自动配置失效：先看 `autoconfigure/TransferEncryptAutoConfiguration.java` 与 `META-INF/spring.factories`

## 7. 测试入口

核心测试文件：

- `src/test/java/io/github/jasper/transfer/encrypt/TransferEncryptionIntegrationTest.java`
- `src/test/java/io/github/jasper/transfer/encrypt/TransferEncryptionFilterRegistrationIntegrationTest.java`
- `src/test/java/io/github/jasper/transfer/encrypt/TransferEncryptionFilterOrderIntegrationTest.java`
- `src/test/java/io/github/jasper/transfer/encrypt/TransferEncryptionFeignChainIntegrationTest.java`

这些测试分别覆盖：

- JSON / form / query 解密与响应加密
- 文件上传与下载 MD5
- 业务侧存在其他 `FilterRegistrationBean` 时本 Filter 仍能注册
- 自定义 `filter-order` 生效
- Feign 三跳链路、注解选择性接管、MD5 开关与优先级

## 8. 验证命令

模块主验证命令：

```powershell
cd .\spring2-plugin
mvn "-Dmaven.repo.local=.m2repo" test
```

打包验证：

```powershell
cd .\spring2-plugin
mvn "-Dmaven.repo.local=.m2repo" -DskipTests package
```

如果改动影响到端到端链路，继续验证：

```powershell
.\scripts\run-e2e-demo.ps1
```

或：

```powershell
.\scripts\run-e2e-cross-stack.ps1
```

## 9. 文档参考

改动涉及架构、过滤器顺序或 Spring 3 方向时，优先同步查看：

- `README.md`
- `docs/transport-encryption-architecture.md`
- `docs/filter-ordering-guide.md`
- `docs/spring3-compatibility-plan.md`
