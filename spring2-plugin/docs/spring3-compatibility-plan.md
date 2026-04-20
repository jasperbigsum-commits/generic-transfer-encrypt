# Spring 3 Compatibility Plan

本文说明当前 `spring2-plugin` 扩展到 Spring Boot 3 / Spring Framework 6 的建议路径，以及为什么不建议在一个 starter 里直接混合双版本依赖。

## 1. 当前现状

当前 `spring2-plugin` 明确面向：

- Spring Boot `2.x`
- Spring Framework `5.x`
- `javax.servlet.*`

当前模块内的主要耦合点包括：

- `TransferEncryptionFilter`
- `TransferHttpServletRequestWrapper`
- `TransferMultipartIntegrityInterceptor`
- `TransferWebUtils`

这些类都直接依赖 `javax.servlet.*`，这使得它们无法原样在 Spring Boot 3 中复用。

## 2. Spring Boot 3 的主要变化

从 Spring Boot 2 升到 3，至少有三类差异：

1. Servlet API 命名空间变化

- Spring Boot 2：`javax.servlet.*`
- Spring Boot 3：`jakarta.servlet.*`

2. 自动配置注册方式变化

- Spring Boot 2 常见做法：`META-INF/spring.factories`
- Spring Boot 3 推荐做法：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

3. Spring Cloud / OpenFeign 版本矩阵变化

- Spring Cloud 2021.x 对应 Boot 2.7
- Spring Cloud 2022.x / 2023.x 对应 Boot 3.x

这意味着当前 starter 不能只改一个依赖版本号就完成兼容。

## 3. 为什么不建议做“单 starter 双兼容”

理论上可以通过反射、条件装配、双套源码或多 release jar 去兼容，但工程代价和维护成本都偏高。

不建议的原因：

- `javax.servlet` 与 `jakarta.servlet` 不能在同一份源码里优雅共存
- 条件反射会把编译期问题拖到运行期，排错成本更高
- 自动配置、测试矩阵、Feign 版本都要分叉
- 用户侧很难判断到底应该引哪个依赖

结论：更稳妥的方案是“核心复用 + 适配层拆分”。

## 4. 建议的拆分方式

推荐拆成以下模块：

```text
generic-transfer-encrypt-core
generic-transfer-encrypt-feign-common
generic-transfer-encrypt-spring2-starter
generic-transfer-encrypt-spring3-starter
```

### 4.1 `generic-transfer-encrypt-core`

放纯协议与加密能力：

- 传输信封模型
- `SM2/SM4` 加解密服务
- 请求/响应编解码核心
- 与 Web 无关的工具类

要求：

- 不依赖 `javax.servlet`
- 不依赖 `jakarta.servlet`
- 不依赖具体 Spring Web API

### 4.2 `generic-transfer-encrypt-feign-common`

放尽量和 Spring 版本无关的 Feign 包装逻辑：

- Feign request/response 编解码
- 公钥别名选择规则
- 线程上下文透传

注意：

- 若最终仍依赖某些 Spring 版本特定 API，也可以继续分为 `feign-spring2` / `feign-spring3`
- 但第一步至少应把“协议处理逻辑”和“Spring Web 适配层”分开

### 4.3 `generic-transfer-encrypt-spring2-starter`

只处理 Spring Boot 2 适配：

- `javax.servlet.*`
- `spring.factories`
- Spring Cloud 2021.x

### 4.4 `generic-transfer-encrypt-spring3-starter`

只处理 Spring Boot 3 适配：

- `jakarta.servlet.*`
- `AutoConfiguration.imports`
- Spring Cloud 2022.x / 2023.x

## 5. 依赖收敛建议

为了尽量避免非必要依赖，建议按职责保留：

必需依赖：

- Spring Boot 自动配置相关依赖
- Spring Web MVC 适配层依赖
- `bcprov` / 国密实现所需依赖
- Jackson（若需要 JSON 解析）

尽量不要放进公共核心模块的依赖：

- `javax.servlet-api`
- `jakarta.servlet-api`
- 仅为简化代码而存在的工具依赖
- 与 Spring Cloud 特定版本强耦合的 starter

当前仓库已先做的一步收敛：

- 主代码已移除 `lombok`
- 不再单独声明 `hutool-core` 直连依赖

## 6. E2E 与测试矩阵建议

Spring 3 starter 落地后，建议同步补齐两套 E2E 服务：

```text
e2e-demo/
├─ server-spring2/
├─ server-spring3/
└─ tests/
```

并分别保留：

1. 手动联调版
2. 自动探针版

推荐测试矩阵：

- `spring2-starter + e2e-demo/server-spring2`
- `spring3-starter + e2e-demo/server-spring3`
- `vanilla-js-plugin`
- `vue3-plugin`
- `flutter-plugin`

## 7. 推荐实施顺序

建议按以下顺序推进，而不是一次性重构：

1. 先抽 `core`
2. 再把当前 `spring2-plugin` 改名或收敛为 `spring2-starter`
3. 新建 `spring3-starter`
4. 最后补齐 `e2e-demo/server-spring3` 与自动测试脚本

这样风险最小，也更容易定位回归。
