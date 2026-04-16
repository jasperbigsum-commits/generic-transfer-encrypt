# generic-transfer-encrypt-spring2-plugin

面向 Spring Boot 2 / Spring Web MVC 的通用传输层加密插件。

详细架构图与请求时序见：

- [docs/transport-encryption-architecture.md](E:\IdeaProject\generic-transfer-encrypt\spring2-plugin\docs\transport-encryption-architecture.md)

## 能力范围

- `SM2` 交换单次请求随机 `SM4` 密钥。
- `JSON`、`application/x-www-form-urlencoded`、`query param` 自动解密，不影响 `@RequestBody`、`@RequestParam`、`@ModelAttribute` 原有绑定。
- 响应体自动二次封装为加密信封，Controller 无需改返回对象。
- 文件上传/下载默认跳过加密，仅校验/回传 `MD5`。
- 通过路径正则配置启停。
- `OpenFeign` 出站请求与入站响应遵循同一协议。

## 协议

请求信封字段：

- `encryptedKey`: 前端用服务端 `SM2 public key` 加密后的随机 `SM4 key`
- `encryptedData`: 用该 `SM4 key` 加密后的明文
- `contentMd5`: 明文 `MD5`

响应信封字段：

- `encryptedData`
- `contentMd5`
- `originalContentType`
- `timestamp`

文件传输：

- 响应头 `X-Transfer-Content-MD5`
- `multipart/form-data` 默认读取 `__md5_<fieldName>` 参数做文件完整性校验

## Maven

```xml
<dependency>
    <groupId>io.github.jasperbigsum-commits</groupId>
    <artifactId>generic-transfer-encrypt-spring2-plugin</artifactId>
    <version>1.0</version>
</dependency>
```

## 配置

```yaml
transfer:
  encrypt:
    enabled: true
    feign-enabled: true
    private-key: 你的SM2私钥
    public-key: 你的SM2公钥
    include-path-regex:
      - ^/api/.*
      - ^/feign/.*
    exclude-path-regex:
      - ^/api/public/.*
    multipart-md5-field-prefix: __md5_
    feign-public-keys:
      service-b: 下游服务B的SM2公钥
      service-c.internal: 下游服务C的SM2公钥
```

## OpenFeign

插件会自动接管底层 `feign.Client`，但只有显式标记的 Feign 接口调用才会开启传输加密。

先在 Feign 接口上增加自定义注解：

```java
@TransferEncryptedFeignClient(publicKeyAlias = "default-order-key", md5Enabled = true)
@FeignClient(name = "order-service", url = "${order.service.url}")
public interface OrderServiceClient {
    @TransferEncryptedFeignClient(publicKeyAlias = "query-order-key", md5Enabled = false)
    @PostMapping("/orders/query")
    OrderResponse query(@RequestBody OrderRequest request);
}
```

- 命中正则路径时，`GET/DELETE` 的 query 会被封装后再发送。
- `application/json` 请求体会被转成 JSON 加密信封。
- `application/x-www-form-urlencoded` 请求体会被转成表单加密信封。
- 二进制/文件请求只追加 `MD5`。
- 接口或方法注解上都可通过 `publicKeyAlias` 显式指定下游公钥别名。
- 接口或方法注解上都可通过 `md5Enabled` 控制是否启用额外的 `X-Transfer-Content-MD5` 头校验。
- 优先级：方法注解 `publicKeyAlias` > 接口注解 `publicKeyAlias` > `host/serviceId` 映射 > 全局默认公钥。
- `md5Enabled` 只影响 Feign 额外 MD5 头，不影响加密信封内部 `contentMd5`。
- 可通过 `transfer.encrypt.feign-public-keys.<serviceId或host>` 为不同下游服务配置不同公钥。
- 未标记 `@TransferEncryptedFeignClient` 的 Feign 接口保持原样，不会被误包。

如果你有自定义 `Client`，确保最终 Bean 仍然暴露为 `feign.Client`，这样包装器才能接管。

## 前端

原生 JS SDK 位于：

- `../vanilla-js-plugin/transfer-encrypt.js`

SDK 依赖浏览器侧 `sm-crypto` 全局对象，示例见：

- `../vanilla-js-plugin/README.md`

内网离线使用时不要引用 CDN。仓库已经提供本地资源：

- `../vanilla-js-plugin/vendor/sm-crypto.min.js`

直接通过本地 `<script>` 或内部构建产物注入即可。
