# generic_transfer_encrypt_flutter

`generic-transfer-encrypt` 的 Flutter 客户端实现，协议与仓库里的 Spring 端、原生 JS 端保持一致。

能力范围：

- `SM2 + SM4` 请求信封加密
- `JSON`、`application/x-www-form-urlencoded`、`GET/DELETE query` 自动封装
- `multipart/form-data` 文件上传自动追加 `__md5_<fieldName>`
- 文件下载自动校验 `X-Transfer-Content-MD5`
- 支持普通明文结果请求和二进制下载请求
- 适合 Flutter `Android / iOS / Windows / macOS / Linux / Web` 统一调用层

详细文档：

- [接入指南](./docs/flutter-plugin-guide.md)
- [API 说明](./docs/flutter-plugin-api.md)
- [FAQ](./docs/flutter-plugin-faq.md)
- [示例说明](./example/README.md)

## 协议对齐

外层传输字段：

- `transferPayload`
- `originalContentType`

`transferPayload` 内部载荷字段：

- `encryptedKey`
- `encryptedData`
- `contentMd5`
- `timestamp`

文件上传完整性字段：

- 单文件：`__md5_<fieldName>`
- 多文件：`__md5_<fieldName>__0`、`__md5_<fieldName>__1`

## 安装

```yaml
dependencies:
  generic_transfer_encrypt_flutter:
    path: ../flutter-plugin
```

如果你准备拆出去单独发布，再把 `path` 改成你自己的制品来源即可。

## 初始化

```dart
import 'package:generic_transfer_encrypt_flutter/flutter_transfer_encrypt.dart';

final client = TransferEncryptClient(
  baseUrl: 'http://localhost:8080',
  publicKey: '服务端 SM2 公钥',
);
```

推荐做法：

- 在应用基础设施层初始化成单例
- 在业务网关层封装接口，不要在页面直接拼协议
- `Authorization`、租户头、traceId 在业务层统一注入

如果你的项目统一使用 `dio`，也可以直接使用 `TransferEncryptDioAdapter`，不需要放弃现有网络层。

## JSON 请求

```dart
final result = await client.postJson(
  '/api/json',
  body: <String, dynamic>{'name': 'alice'},
);
```

通用写法：

```dart
final result = await client.request(
  url: '/api/json',
  method: 'POST',
  json: <String, dynamic>{'name': 'alice'},
);
```

## 表单请求

```dart
final result = await client.postForm(
  '/api/form',
  body: <String, dynamic>{'name': 'carol'},
);
```

## GET / query

```dart
final result = await client.get(
  '/api/query',
  params: <String, dynamic>{'name': 'bob'},
);
```

## 文件上传

单文件：

```dart
final result = await client.uploadSingle(
  '/api/upload',
  fields: <String, dynamic>{'bizType': 'mobile-demo'},
  file: TransferEncryptFile(
    fieldName: 'file',
    filename: 'avatar.png',
    bytes: await imageBytes(),
  ),
);
```

多文件上传：

```dart
final result = await client.upload(
  '/api/upload/multi',
  files: <TransferEncryptFile>[
    TransferEncryptFile(
      fieldName: 'files',
      filename: 'a.txt',
      bytes: utf8.encode('A'),
    ),
    TransferEncryptFile(
      fieldName: 'files',
      filename: 'b.txt',
      bytes: utf8.encode('B'),
    ),
  ],
);
```

说明：

- 上传链路默认不加密文件内容，只补充 MD5 完整性字段
- 如果同一个字段名上传多个文件，SDK 会自动按索引生成 `__md5_<fieldName>__<index>`
- 可通过 `TransferEncryptFile.fromText(...)` 快速生成文本文件

## 文件下载

普通下载：

```dart
final binary = await client.download('/api/file');
final bytes = binary.bytes;
```

如果服务端返回 `X-Transfer-Content-MD5`，客户端会自动校验。

如果导出接口需要先提交 JSON / form，再返回二进制结果，可以直接使用：

```dart
final binary = await client.requestBinary(
  url: '/api/export',
  method: 'POST',
  json: <String, dynamic>{'bizId': 1001},
);
```

## Dio 接入

```dart
final dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080'));

final adapter = TransferEncryptDioAdapter(
  dio: dio,
  publicKey: '服务端 SM2 公钥',
);

final result = await adapter.postJson(
  '/api/json',
  body: <String, dynamic>{'name': 'dio-client'},
);
```

适合场景：

- 现有项目已经统一使用 `dio`
- 需要复用现有拦截器、超时、代理、日志和证书配置

## 错误处理

```dart
try {
  await client.postJson('/api/json', body: {'name': 'alice'});
} on TransferEncryptHttpException catch (error) {
  print(error.statusCode);
  print(error.responseBody);
}
```

说明：

- 普通请求收到加密错误体时，会先解密再抛异常
- 二进制请求收到 JSON 错误体时，也会尽量解析后放进 `responseBody`

## 生命周期

如果你把 `TransferEncryptClient` 放成长期单例，页面销毁时通常不需要主动关闭。

如果你是短生命周期自行创建，也可以在结束时调用：

```dart
client.close();
```

## 示例

可直接参考：

- `example/lib/main.dart`

这个示例只演示调用方式，不依赖特定状态管理框架。

## Android AVD 测试

如果你手边没有 Android 真机，推荐直接使用 Android Emulator / AVD 调试，不要依赖已经退役的 `Windows Subsystem for Android`。

建议准备项：

- 已安装 `Flutter SDK`
- 已安装 `Android SDK`
- 已接受 Android licenses
- 已安装至少一个 Android system image

可先检查环境：

```powershell
flutter doctor -v
flutter emulators
```

如果本机还没有可用 AVD，可用 Android Studio 创建，或者直接用命令行：

```powershell
sdkmanager "emulator" "platform-tools" "platforms;android-35" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n flutter_api35 -k "system-images;android-35;google_apis;x86_64" -d "medium_phone"
```

查看和启动 AVD：

```powershell
emulator -list-avds
emulator -avd flutter_api35
```

启动完成后，确认 Flutter 能看到模拟器：

```powershell
flutter devices
```

然后进入示例工程运行：

```powershell
cd .\example
flutter pub get
flutter run -d emulator-5554
```

如果你不确定设备 ID，也可以直接先运行：

```powershell
flutter run
```

常见排查：

- `flutter doctor -v` 里 `Android toolchain` 未通过：先补 `ANDROID_HOME` / `ANDROID_SDK_ROOT`
- `flutter devices` 看不到模拟器：确认 AVD 已真正启动，而不是只创建了配置
- 首次 `build apk` 或 `flutter run` 速度较慢：通常是在下载 Gradle、NDK、CMake 或构建缓存
- Windows 下建议优先使用 `x86_64` system image，并确认 `emulator -accel-check` 可通过
