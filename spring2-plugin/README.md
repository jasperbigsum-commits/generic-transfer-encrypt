# generic-transfer-encrypt-spring2-plugin

面向 Spring Boot 2 / Spring Web MVC 的通用传输层加密插件。

详细架构图与请求时序见：

- [docs/transport-encryption-architecture.md](E:\IdeaProject\generic-transfer-encrypt\spring2-plugin\docs\transport-encryption-architecture.md)
- [docs/filter-ordering-guide.md](E:\IdeaProject\generic-transfer-encrypt\spring2-plugin\docs\filter-ordering-guide.md)

## 能力范围

- `SM2` 交换单次请求随机 `SM4` 密钥。
- `JSON`、`application/x-www-form-urlencoded`、`query param` 自动解密，不影响 `@RequestBody`、`@RequestParam`、`@ModelAttribute` 原有绑定。
- 响应体自动二次封装为加密信封，Controller 无需改返回对象。
- 文件上传/下载默认跳过加密，仅校验/回传 `MD5`。
- 通过路径正则配置启停。
- `OpenFeign` 出站请求与入站响应遵循同一协议。

## 协议

外层传输字段：

- `transferPayload`
- `originalContentType`

`transferPayload` 内部载荷字段：

- `encryptedKey`: 请求时前端用服务端 `SM2 public key` 加密后的随机 `SM4 key`
- `encryptedData`: 用该 `SM4 key` 加密后的明文
- `contentMd5`: 明文 `MD5`
- `timestamp`: 发送时间戳

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

## 自建包构建与引用

本地安装到 Maven 仓库：

```powershell
cd .\spring2-plugin
mvn "-Dmaven.repo.local=.m2repo" clean install
```

发布到内部 Maven/Nexus：

```powershell
cd .\spring2-plugin
mvn "-Dmaven.repo.local=.m2repo" clean deploy `
  "-DaltDeploymentRepository=internal::default::https://your-maven-host/repository/releases"
```

业务项目中引用时，优先保持：

- 插件版本与服务端协议版本同步
- 业务项目自行提供 `spring-boot-starter-web`
- 若使用 OpenFeign，再由业务项目显式引入 `spring-cloud-starter-openfeign`

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
    filter-order: -10
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

## FilterRegistrationBean 冲突说明

如果业务侧自己注册了 `FilterRegistrationBean<Filter>`，旧版本插件可能因为自动配置退让条件过宽，导致传输层加解密 filter 没有注册。

当前版本已修复：

- 插件改为按具体 `TransferEncryptionFilter` 类型判断是否缺失
- 注册 Bean 改为独立的 `transferEncryptionFilterRegistrationBean`
- 不会再因为业务侧存在其他 `FilterRegistrationBean` 就整体失效

如果仍然存在顺序冲突，例如业务 filter 在我们之前就消费了请求体、改写了请求或提前返回，可以通过下面配置把传输层 filter 提前：

```yaml
transfer:
  encrypt:
    filter-order: -100
```

建议：

- 让传输层加解密 filter 先于会读取请求体的业务 filter 执行
- 若业务侧确实需要自定义注册本插件 filter，可直接覆盖 `transferEncryptionFilterRegistrationBean`

## 前端

原生 JS SDK 位于：

- `../vanilla-js-plugin/transfer-encrypt.js`

SDK 依赖浏览器侧 `sm-crypto` 全局对象，示例见：

- `../vanilla-js-plugin/README.md`

内网离线使用时不要引用 CDN。仓库已经提供本地资源：

- `../vanilla-js-plugin/vendor/sm-crypto.min.js`

直接通过本地 `<script>` 或内部构建产物注入即可。

Flutter 客户端位于：

- `../flutter-plugin/README.md`

详细接入文档见：

- `../flutter-plugin/docs/flutter-plugin-guide.md`

## Spring 3 扩展兼容说明

当前模块名叫 `spring2-plugin`，是因为它明确面向：

- Spring Boot 2.x
- Spring Framework 5.x
- `javax.servlet.*`

它不能在“不拆分适配层”的前提下零改动兼容 Spring Boot 3，主要原因有两类：

1. Servlet 命名空间变化  
   当前 Web 层大量使用 `javax.servlet.*`，而 Spring Boot 3 要求 `jakarta.servlet.*`。

2. 自动配置装配方式差异  
   当前资源文件使用 `META-INF/spring.factories`，Spring Boot 3 更推荐 `AutoConfiguration.imports`。

推荐兼容策略不是在一个 starter 里硬塞两套依赖，而是：

1. 抽 `core`  
   放协议、模型、加密编解码，不依赖 Servlet。
2. 保留 `spring2-plugin`
   只承担 `javax.servlet` 版本适配。
3. 新增 `spring3-plugin`
   只承担 `jakarta.servlet` 版本适配。
4. 视情况抽 `feign-common`
   把 Feign 包装逻辑做成可复用模块，减少两边重复代码。

当前仓库已经先做了一步依赖收敛：

- 主代码已移除 `lombok`
- 不再单独声明 `hutool-core` 直连依赖

更完整的拆分建议见：

- [docs/spring3-compatibility-plan.md](E:\IdeaProject\generic-transfer-encrypt\spring2-plugin\docs\spring3-compatibility-plan.md)
