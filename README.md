# generic-transfer-encrypt

通用传输层加密方案仓库，目标是在业务代码尽量无侵入的前提下，为 `Spring Boot 2` 服务端、浏览器端和 Flutter 客户端提供一致的应用层传输加密协议。

当前已落地模块：

- `spring2-plugin`: Spring Boot 2 / Spring MVC 服务端插件
- `vanilla-js-plugin`: 原生 JS 浏览器端 SDK
- `flutter-plugin`: Flutter 客户端 SDK
- `vue3-plugin`: Vue 3 客户端 SDK

## 核心能力

- 使用服务端 `SM2 publicKey` 交换单次请求随机 `SM4 key`
- 支持 `JSON`、`form-urlencoded`、`GET/DELETE query` 加密
- 响应体使用同一次请求协商出的 `SM4 key` 返回
- 文件上传/下载默认不做内容加密，只做 `MD5` 完整性校验
- 浏览器端、Flutter 端、服务端遵循同一套信封字段

## 协议概览

外层传输字段：

- `transferPayload`
- `originalContentType`

`transferPayload` 内部载荷字段：

- `encryptedKey`
- `encryptedData`
- `contentMd5`
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
- [vue3-plugin/README.md](E:\IdeaProject\generic-transfer-encrypt\vue3-plugin\README.md)
- [e2e-demo/README.md](E:\IdeaProject\generic-transfer-encrypt\e2e-demo\README.md)

## 三大环境与目录架构

当前仓库按三大运行环境组织：

1. 服务端环境
   负责 Spring MVC 入站解密、响应加密、OpenFeign 出站加密。
2. 浏览器环境
   提供原生 JS SDK 与 Vue 3 SDK，适合静态页面和 Vue 项目。
3. Flutter 环境
   提供 `package:http` 与 `dio` 两套客户端适配。

服务端相关目录：

```text
spring2-plugin/
├─ src/main/java/
│  ├─ annotation/         # Feign 选择性加密注解
│  ├─ autoconfigure/      # Spring Boot 自动配置入口
│  ├─ config/             # 配置属性与 MVC 配置
│  ├─ core/               # 协议编解码核心
│  ├─ crypto/             # 国密加解密实现
│  ├─ feign/              # OpenFeign 包装层
│  ├─ model/              # 传输模型
│  ├─ util/               # Web/JSON 辅助工具
│  └─ web/                # Filter、RequestWrapper、上传完整性校验
├─ src/main/resources/
│  └─ META-INF/           # spring.factories
├─ src/test/java/         # Spring Boot 2 集成测试
└─ docs/                  # 架构与过滤器顺序说明
```

浏览器相关目录：

```text
vanilla-js-plugin/
├─ transfer-encrypt.js    # 原生 JS SDK
├─ vendor/                # 本地离线依赖
├─ example/               # 静态示例页面
└─ tests/                 # Node 原生测试

vue3-plugin/
├─ transfer-encrypt-vue3-core.js
├─ transfer-encrypt-vue3.js
├─ docs/
├─ example/
└─ tests/
```

Flutter 相关目录：

```text
flutter-plugin/
├─ lib/
│  ├─ flutter_transfer_encrypt.dart
│  └─ src/                # client / dio / protocol / model
├─ test/
├─ docs/
└─ example/               # Windows / Web / Android 示例工程
```

E2E 相关目录：

```text
e2e-demo/
├─ server/                # 当前 Spring Boot 2 演示服务
└─ tests/                 # Node cross-stack 探针
```

## 自建包构建与引用

### 1. Spring Boot 插件

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

业务项目引用：

```xml
<dependency>
    <groupId>io.github.jasperbigsum-commits</groupId>
    <artifactId>generic-transfer-encrypt-spring2-plugin</artifactId>
    <version>1.0</version>
</dependency>
```

### 2. 原生 JS 插件

`vanilla-js-plugin` 当前是纯静态资源，不依赖打包工具。推荐两种方式：

1. 直接复制 `transfer-encrypt.js` 与 `vendor/` 到你们自己的静态资源目录
2. 在内部制品库中发布一个压缩包，例如 `generic-transfer-encrypt-vanilla-js-1.0.0.zip`

引用方式：

```html
<script src="/assets/generic-transfer-encrypt/vendor/md5.js"></script>
<script src="/assets/generic-transfer-encrypt/vendor/sm-crypto.min.js"></script>
<script src="/assets/generic-transfer-encrypt/transfer-encrypt.js"></script>
```

### 3. Vue 3 插件

打包内部 npm 包：

```powershell
cd .\vue3-plugin
npm pack
```

会生成类似：

```text
generic-transfer-encrypt-vue3-1.0.0.tgz
```

业务项目引用方式：

```json
{
  "dependencies": {
    "generic-transfer-encrypt-vue3": "file:../artifacts/generic-transfer-encrypt-vue3-1.0.0.tgz"
  }
}
```

如果你们有内部 npm 仓库，也可以发布后按常规版本号引用。

### 4. Flutter 插件

当前推荐三种引用方式：

1. 仓库内联调：`path`
2. 内部 Git：`git`
3. 内部 pub 仓库：发布后按版本引用

仓库内联调：

```yaml
dependencies:
  generic_transfer_encrypt_flutter:
    path: ../flutter-plugin
```

内部 Git：

```yaml
dependencies:
  generic_transfer_encrypt_flutter:
    git:
      url: ssh://git@your-git-host/mobile/generic-transfer-encrypt.git
      path: flutter-plugin
      ref: main
```

如果后续要正式对外或对内托管，建议再拆成独立 Flutter package 仓库或内部 pub 包。

## Spring 3 兼容策略

当前仓库已落地的是 `spring2-plugin`，它可以扩展到 Spring Boot 3，但不建议在现有 starter 上直接混装 `spring2 + spring3` 依赖做兼容开关。

原因：

- 当前 Web 层直接依赖 `javax.servlet.*`
- Spring Boot 3 / Spring Framework 6 已切到 `jakarta.servlet.*`
- 自动配置注册方式也从 `spring.factories` 逐步迁移到 `AutoConfiguration.imports`

建议的扩展方式是：

1. 抽出协议与加密核心模块，不依赖 Servlet 命名空间
2. 保留 `spring2-plugin` 作为 `javax.servlet` 版本 starter
3. 新增 `spring3-plugin` 或 `spring3-starter`，只承载 `jakarta.servlet` 适配层
4. Feign 相关包装尽量抽成公共模块，避免两边重复实现
5. 非必要依赖尽量只保留在具体适配层，不放进公共核心模块

当前仓库已先做的一步收敛：

- `spring2-plugin` 主代码已移除 `lombok` 依赖
- `hutool-core` 不再单独直连声明，继续由 `hutool-crypto` 传递提供

更详细的 Spring 3 扩展说明见：

- [spring2-plugin/docs/spring3-compatibility-plan.md](E:\IdeaProject\generic-transfer-encrypt\spring2-plugin\docs\spring3-compatibility-plan.md)

Flutter 补充文档：

- [flutter-plugin/docs/flutter-plugin-guide.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\docs\flutter-plugin-guide.md)
- [flutter-plugin/docs/flutter-plugin-api.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\docs\flutter-plugin-api.md)
- [flutter-plugin/docs/flutter-plugin-faq.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\docs\flutter-plugin-faq.md)

Vue 3 补充文档：

- [vue3-plugin/docs/vue3-plugin-guide.md](E:\IdeaProject\generic-transfer-encrypt\vue3-plugin\docs\vue3-plugin-guide.md)
- [vue3-plugin/docs/vue3-plugin-api.md](E:\IdeaProject\generic-transfer-encrypt\vue3-plugin\docs\vue3-plugin-api.md)
- [vue3-plugin/docs/vue3-plugin-faq.md](E:\IdeaProject\generic-transfer-encrypt\vue3-plugin\docs\vue3-plugin-faq.md)

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

Flutter 端可直接以 path 方式引用，当前同时支持：

- 基于 `package:http` 的客户端
- 基于 `dio` 的适配层

依赖方式：

```yaml
dependencies:
  generic_transfer_encrypt_flutter:
    path: ../flutter-plugin
```

完整接入见：

- [flutter-plugin/README.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\README.md)

### 4. Vue 3 端

Vue 3 端支持：

- 纯协议 core client
- `app.use(...)` 插件注入
- `provide/inject`
- `useTransferEncryptRequest(...)` composable

完整接入见：

- [vue3-plugin/README.md](E:\IdeaProject\generic-transfer-encrypt\vue3-plugin\README.md)

### 5. 端到端 Demo

仓库已补一个本地联调示例应用：

- [e2e-demo/README.md](E:\IdeaProject\generic-transfer-encrypt\e2e-demo\README.md)

可直接启动演示服务并打开浏览器页面，联调：

- JSON
- form
- query
- 文件上传
- 二进制下载
- OpenFeign 端到端链路

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

`flutter-plugin/example` Android AVD 联调

如果你没有 Android 真机，可直接使用 Android Emulator / AVD 对 Flutter 示例工程做本地联调。

建议先确认环境：

```powershell
flutter doctor -v
flutter emulators
```

如果本机还没有 AVD，可先准备一套常用 Android 35 模拟器：

```powershell
sdkmanager "emulator" "platform-tools" "platforms;android-35" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n flutter_api35 -k "system-images;android-35;google_apis;x86_64" -d "medium_phone"
```

启动模拟器：

```powershell
emulator -list-avds
emulator -avd flutter_api35
```

确认 Flutter 已识别设备：

```powershell
flutter devices
```

进入示例工程运行：

```powershell
cd .\flutter-plugin\example
flutter pub get
flutter run -d emulator-5554
```

如果设备 ID 不确定，也可以直接执行：

```powershell
flutter run
```

### E2E 两类测试入口

当前 E2E 已有两类测试/联调入口，分别对应“人工联调版”和“自动探针版”：

1. 手动联调版  
   安装 `spring2-plugin` 后，通过 `spring-boot:run` 启动演示服务，再打开浏览器页面手工验证。

```powershell
.\scripts\run-e2e-demo.ps1
```

2. 自动探针版  
   先构建 fat jar，再由脚本拉起服务并执行 Node cross-stack probe，适合快速回归。

```powershell
.\scripts\run-e2e-cross-stack.ps1
```

说明：

- 当前落地的 E2E 服务只有 Spring Boot 2 版本
- Spring Boot 3 版本建议在后续拆出 `spring3-plugin` 后，再补一个并列的 `e2e-demo/server-spring3`
- 到那时推荐保持同样两类入口：`manual demo` + `automated probe`

### 仓库级全局集成脚本

仓库根目录新增了统一入口：

- [scripts/run-local-integration-tests.ps1](E:\IdeaProject\generic-transfer-encrypt\scripts\run-local-integration-tests.ps1)
- [scripts/run-local-integration-tests.cmd](E:\IdeaProject\generic-transfer-encrypt\scripts\run-local-integration-tests.cmd)
- [scripts/run-e2e-demo.ps1](E:\IdeaProject\generic-transfer-encrypt\scripts\run-e2e-demo.ps1)
- [scripts/run-e2e-demo.cmd](E:\IdeaProject\generic-transfer-encrypt\scripts\run-e2e-demo.cmd)

默认会尝试执行：

1. `spring2-plugin`: `mvn -Dmaven.repo.local=.m2repo test`
2. `vanilla-js-plugin`: `node tests/transfer-encrypt.native.test.js`
3. `vue3-plugin`: `node tests/transfer-encrypt-vue3.native.test.mjs`
4. `e2e-demo/server`: `mvn -Dmaven.repo.local=..\..\spring2-plugin\.m2repo test`
5. `flutter-plugin`: `flutter pub get`
6. `flutter-plugin`: `flutter test`
7. `flutter-plugin/example`: `flutter pub get`

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
- `-SkipVue3`
- `-SkipE2EDemo`
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
- Flutter 客户端 SDK、示例、文档、测试、`dio` 适配层
- Vue 3 客户端 SDK、示例、文档、原生测试
- 本地端到端联调示例应用（含 OpenFeign 端到端链路）
- 仓库级本地集成测试脚本

待继续推进：
