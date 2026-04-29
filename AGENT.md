# AGENT.md

## 1. 仓库定位

这是一个“跨端一致”的应用层传输加密协议仓库，当前已落地以下模块：

- `spring2-plugin`：Spring Boot 2 / Spring MVC 服务端 starter
- `vanilla-js-plugin`：原生浏览器 JavaScript SDK
- `vue3-plugin`：基于同一协议的 Vue 3 SDK
- `flutter-plugin`：Flutter 客户端 SDK
- `e2e-demo`：本地端到端联调与回归验证环境

该仓库的核心目标不是让各端独立演化，而是保证协议行为一致。

## 2. 协议不变量

除非明确是“跨端协议升级”，并且会同步更新所有实现，否则不要随意修改以下字段名或结构。

外层传输字段：

- `transferPayload`
- `originalContentType`

`transferPayload` 内层字段：

- `encryptedKey`
- `encryptedData`
- `contentMd5`
- `timestamp`

文件完整性字段：

- 单文件：`__md5_<fieldName>`
- 多文件：`__md5_<fieldName>__<index>`
- 下载响应头：`X-Transfer-Content-MD5`

以下请求形态需要在各端保持一致：

- `application/json`
- `application/x-www-form-urlencoded`
- `GET/DELETE` query 加密

当前文件上传/下载默认是“完整性校验”，不是“文件内容加密”。

## 3. 模块地图

### `spring2-plugin`

面向 Spring Boot 2.x / Spring Framework 5.x / `javax.servlet.*` 的服务端实现。

重点目录：

- `src/main/java/.../core`：协议编解码与常量
- `src/main/java/.../crypto`：SM2/SM4 实现
- `src/main/java/.../web`：Servlet Filter、RequestWrapper、multipart MD5 校验
- `src/main/java/.../feign`：选择性 OpenFeign 加密链路
- `src/main/java/.../autoconfigure`：starter 自动装配入口
- `src/main/resources/META-INF/spring.factories`：自动配置注册

约束：

- 目标 Java 8
- `pom.xml` 中 Spring Boot 版本为 `2.7.18`
- 使用的是 `javax.servlet`，不是 `jakarta.servlet`
- Spring Boot 3 兼容应走独立模块，不要直接把 Boot 3 API 混入当前 starter

### `vanilla-js-plugin`

纯静态浏览器 SDK，不依赖 npm 构建产物才能运行。

重点目录：

- `transfer-encrypt.js`：主 SDK
- `vendor/`：离线依赖
- `example/`：静态示例页面，含 Layui 适配
- `tests/transfer-encrypt.native.test.js`：Node 原生测试

约束：

- 必须保持可离线使用
- 不要引入只能依赖 CDN 的方案
- 不要假设运行时一定存在 bundler 或 `node_modules`

### `vue3-plugin`

对同一协议的 Vue 3 封装层。

重点目录：

- `transfer-encrypt-vue3-core.js`：协议客户端
- `transfer-encrypt-vue3.js`：Vue plugin / composable 封装
- `tests/transfer-encrypt-vue3.native.test.mjs`
- `docs/` 与 `example/`

约束：

- 包是 ESM（`"type": "module"`）
- `peerDependencies` 依赖 `vue@^3.4.0`
- 除非明确设计需要，不要强绑 `axios`

### `flutter-plugin`

同一协议的 Flutter SDK，既支持通用 client，也支持 Dio 适配层。

重点目录：

- `lib/flutter_transfer_encrypt.dart`：公共导出入口
- `lib/src/transfer_encrypt_client.dart`
- `lib/src/transfer_encrypt_dio_adapter.dart`
- `lib/src/transfer_encrypt_protocol.dart`
- `test/`
- `example/`

约束：

- Dart SDK `>=3.4.0 <4.0.0`
- Flutter `>=3.22.0`
- 要同时兼容普通 client 调用与 Dio 集成

### `e2e-demo`

本地端到端验证环境，用来串起服务端与浏览器链路。

重点目录：

- `server/`：Spring Boot 演示服务
- `tests/client-cross-stack-probe.mjs`：自动探针

说明：

- 当前 demo 服务只有 Spring Boot 2 版本
- 手工联调与自动探针这两类入口都应保留

## 4. 代理工作规则

1. 优先做最小改动，优先保持协议兼容。
2. 如果修改了信封结构、字段名、MD5 行为、二进制处理或请求封装流程，要回看所有客户端实现。
3. 如果问题只属于某个适配层，不要顺手改无关模块。
4. 保持这是一个多运行时仓库，不要为了单端方便破坏其他端一致性。
5. 浏览器侧模块必须继续支持离线/内网场景。
6. 不要把 Spring Boot 3 / `jakarta.*` API 引入 `spring2-plugin`。
7. 不要把 Lombok 重新引回 Spring starter，除非有非常明确的理由。

## 5. 变更影响定位

改动前先判断影响面：

- 协议字段变化：通常会影响 `spring2-plugin`、`vanilla-js-plugin`、`vue3-plugin`、`flutter-plugin` 和 `e2e-demo`
- Servlet filter / 请求解密问题：优先看 `spring2-plugin/web`
- Feign 加密链路问题：优先看 `spring2-plugin/feign`
- 浏览器上传 / Layui 桥接问题：优先看 `vanilla-js-plugin/transfer-encrypt.js` 与 `example/`
- Vue composable / plugin 注入问题：优先看 `vue3-plugin/transfer-encrypt-vue3.js`
- Flutter HTTP 或 Dio 行为问题：优先看 `flutter-plugin/lib/src`
- 端到端回归问题：同时检查 `e2e-demo/server` 与 `e2e-demo/tests`

## 6. 验证命令

优先跑与你改动相关的最小验证集。

仓库级集成脚本：

```powershell
.\scripts\run-local-integration-tests.ps1
```

本地工具不全时可允许跳过：

```powershell
.\scripts\run-local-integration-tests.ps1 -AllowMissingTools
```

模块级验证：

```powershell
cd .\spring2-plugin
mvn "-Dmaven.repo.local=.m2repo" test
```

```powershell
cd .\vanilla-js-plugin
node .\tests\transfer-encrypt.native.test.js
```

```powershell
cd .\vue3-plugin
node .\tests\transfer-encrypt-vue3.native.test.mjs
```

```powershell
cd .\flutter-plugin
flutter pub get
flutter test
```

```powershell
cd .\e2e-demo\server
mvn "-Dmaven.repo.local=..\..\spring2-plugin\.m2repo" test
```

手工 E2E 联调：

```powershell
.\scripts\run-e2e-demo.ps1
```

自动 cross-stack 探针：

```powershell
.\scripts\run-e2e-cross-stack.ps1
```

## 7. 环境要求

按模块不同，可能需要：

- `Java 8+`
- `Maven`
- `Node.js`
- `Flutter SDK`

仓库脚本已经支持在部分工具缺失时做降级执行。

## 8. 建议的改动纪律

- 变更了行为或公共 API 时，同步更新对应 README 或模块文档。
- 改了哪个模块，就补哪个模块的测试。
- 跨端行为变更，至少补一侧服务端验证，再补一侧客户端或 E2E 验证。
- 示例代码要和真实支持的行为保持一致，尤其是上传、query 加密、公钥初始化这几类场景。

## 9. 已知架构方向

Spring Boot 3 兼容的既定方向是“拆分独立兼容路径”，不是在当前 starter 里混装 Boot 2 和 Boot 3 依赖。

如果后续工作涉及该方向，优先遵循现有文档：

- `spring2-plugin/docs/spring3-compatibility-plan.md`
- `spring2-plugin/docs/transport-encryption-architecture.md`
