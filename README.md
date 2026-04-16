# generic-transfer-encrypt

通用传输层加密方案仓库，目标是在业务代码尽量无侵入的前提下，为 `Spring Boot 2` 服务端、浏览器端和 Flutter 客户端提供一致的应用层传输加密协议。

当前已落地模块：

- `spring2-plugin`: Spring Boot 2 / Spring MVC 服务端插件
- `vanilla-js-plugin`: 原生 JS 浏览器端 SDK
- `flutter-plugin`: Flutter 客户端 SDK

当前占位模块：

- `vue3-plugin`: 目录已预留，尚未补完整实现

## 核心能力

- 使用服务端 `SM2 publicKey` 交换单次请求随机 `SM4 key`
- 支持 `JSON`、`form-urlencoded`、`GET/DELETE query` 加密
- 响应体使用同一次请求协商出的 `SM4 key` 返回
- 文件上传/下载默认不做内容加密，只做 `MD5` 完整性校验
- 浏览器端、Flutter 端、服务端遵循同一套信封字段

## 协议概览

请求信封字段：

- `algorithm`
- `encryptedKey`
- `encryptedData`
- `contentMd5`
- `originalContentType`
- `timestamp`

响应信封字段：

- `encryptedData`
- `contentMd5`
- `originalContentType`
- `timestamp`

文件完整性字段：

- 单文件：`__md5_<fieldName>`
- 多文件：`__md5_<fieldName>__<index>`
- 下载响应头：`X-Transfer-Content-MD5`

架构说明和时序图见：

- [spring2-plugin/docs/transport-encryption-architecture.md](E:\IdeaProject\generic-transfer-encrypt\spring2-plugin\docs\transport-encryption-architecture.md)

## 仓库结构

```text
generic-transfer-encrypt/
├─ spring2-plugin/
├─ vanilla-js-plugin/
├─ flutter-plugin/
├─ vue3-plugin/
└─ scripts/
```

各模块入口文档：

- [spring2-plugin/README.md](E:\IdeaProject\generic-transfer-encrypt\spring2-plugin\README.md)
- [vanilla-js-plugin/README.md](E:\IdeaProject\generic-transfer-encrypt\vanilla-js-plugin\README.md)
- [flutter-plugin/README.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\README.md)

Flutter 补充文档：

- [flutter-plugin/docs/flutter-plugin-guide.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\docs\flutter-plugin-guide.md)
- [flutter-plugin/docs/flutter-plugin-api.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\docs\flutter-plugin-api.md)
- [flutter-plugin/docs/flutter-plugin-faq.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\docs\flutter-plugin-faq.md)

## 快速开始

### 1. 服务端

进入 `spring2-plugin`，按模块 README 配置公私钥、路径规则和是否启用 Feign 集成。

核心文档：

- [spring2-plugin/README.md](E:\IdeaProject\generic-transfer-encrypt\spring2-plugin\README.md)

### 2. 浏览器端

浏览器端可直接使用仓库内的静态文件：

- [vanilla-js-plugin/transfer-encrypt.js](E:\IdeaProject\generic-transfer-encrypt\vanilla-js-plugin\transfer-encrypt.js)
- [vanilla-js-plugin/vendor/sm-crypto.min.js](E:\IdeaProject\generic-transfer-encrypt\vanilla-js-plugin\vendor\sm-crypto.min.js)

### 3. Flutter 端

Flutter 端可直接以 path 方式引用：

```yaml
dependencies:
  generic_transfer_encrypt_flutter:
    path: ../flutter-plugin
```

完整接入见：

- [flutter-plugin/README.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\README.md)

## 本地测试

### 分模块测试

`spring2-plugin`

```powershell
cd .\spring2-plugin
mvn "-Dmaven.repo.local=.m2repo" test
```

`vanilla-js-plugin`

```powershell
cd .\vanilla-js-plugin
node .\tests\transfer-encrypt.native.test.js
```

`flutter-plugin`

```powershell
cd .\flutter-plugin
flutter pub get
flutter test
```

### 仓库级全局集成脚本

仓库根目录新增了统一入口：

- [scripts/run-local-integration-tests.ps1](E:\IdeaProject\generic-transfer-encrypt\scripts\run-local-integration-tests.ps1)
- [scripts/run-local-integration-tests.cmd](E:\IdeaProject\generic-transfer-encrypt\scripts\run-local-integration-tests.cmd)

默认会尝试执行：

1. `spring2-plugin`: `mvn -Dmaven.repo.local=.m2repo test`
2. `vanilla-js-plugin`: `node tests/transfer-encrypt.native.test.js`
3. `flutter-plugin`: `flutter pub get`
4. `flutter-plugin`: `flutter test`
5. `flutter-plugin/example`: `flutter pub get`

执行方式：

```powershell
.\scripts\run-local-integration-tests.ps1
```

若你的本机暂时没装完所有工具链，但想先跑已具备条件的模块：

```powershell
.\scripts\run-local-integration-tests.ps1 -AllowMissingTools
```

常用参数：

- `-SkipSpring`
- `-SkipVanillaJs`
- `-SkipFlutter`
- `-IncludeFlutterAnalyze`
- `-AllowMissingTools`
- `-ContinueOnError`

Windows 双击/命令行包装入口：

```cmd
scripts\run-local-integration-tests.cmd
```

## 环境要求

按模块不同，你可能需要：

- `Java 8+`
- `Maven`
- `Node.js`
- `Flutter SDK`

说明：

- 当前仓库环境下，`flutter` 和 `dart` 可能不在 PATH 中；这时全局测试脚本会明确报缺失，或在 `-AllowMissingTools` 下跳过
- 脚本默认不帮你安装依赖，只负责本地预检查和串行执行测试命令

## 安全边界

- 这是应用层传输加密，不替代 HTTPS / mTLS
- 服务端私钥只能保存在服务端
- 公钥分发仍需要由你们自己的可信渠道提供
- `MD5` 只用于快速完整性校验，不是高强度安全签名方案

## 当前状态

已完成：

- Spring Boot 2 服务端传输加密插件
- 原生 JS 客户端 SDK 和本地测试
- Flutter 客户端 SDK、示例、文档、测试
- 仓库级本地集成测试脚本

待继续推进：

- `vue3-plugin`
- 端到端联调示例应用
- `dio` 适配层
