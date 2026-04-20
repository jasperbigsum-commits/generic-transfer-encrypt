# Flutter Plugin FAQ

## 1. 为什么还需要 HTTPS？

这个插件做的是应用层传输加密，不是 TLS 替代。HTTPS 仍然负责：

- 服务端身份校验
- 通道机密性
- 通道级抗中间人

推荐做法是：`HTTPS + 应用层传输加密` 一起使用，而不是二选一。

## 2. 为什么上传文件默认不加密内容？

这是和仓库里的 Spring 端保持一致的设计：

- 大文件二次加密成本高
- 需要流式处理时复杂度更高
- 当前项目优先保证完整性校验和协议统一

所以当前文件链路默认策略是：

- 文件内容不做二次加密
- 通过 `__md5_<fieldName>` 或 `__md5_<fieldName>__<index>` 做完整性校验

## 3. 为什么上传接口也要求传 `publicKey`？

上传本身不使用公钥去加密文件内容，但客户端实例通常不会只调用上传接口，还会同时调用 JSON / form / query 加密接口。当前构造参数统一保留 `publicKey`，是为了避免：

- 同一工程里维护多种客户端模型
- 初始化代码分叉
- 不同接口类型的调用方式不一致

## 4. `publicKey 未配置` 一般是什么原因？

通常是以下几类问题：

- 传入了空字符串
- 文本框里只有空格或换行
- 公钥尚未从配置中心或服务端下发
- 示例页里没有粘贴服务端 SM2 公钥就直接发请求

排查建议：

- 打印初始化入参
- 对 `publicKey.trim()` 结果做判空
- 确认客户端拿到的是服务端公钥，而不是私钥或其他格式文本

## 5. 服务端返回 400 / 403 时还能拿到解密后的错误体吗？

可以。

如果响应仍然是仓库定义的加密 JSON 信封，客户端会先解密，再抛出 `TransferEncryptHttpException`，并把解密结果放进 `responseBody`。

示例：

```dart
try {
  await client.postJson('/api/json', body: {'name': 'alice'});
} on TransferEncryptHttpException catch (error) {
  print(error.statusCode);
  print(error.responseBody);
}
```

## 6. 二进制下载失败时也能拿到 JSON 错误体吗？

可以，前提是服务端实际返回的是 JSON 错误响应，而不是纯二进制内容。

当前实现会优先尝试：

- 判断响应是否为 JSON
- 如果是加密 JSON，则先解密
- 再把解析结果放进 `responseBody`

如果服务端返回的不是 JSON，而是原始二进制错误内容，则异常里会保留原始 bytes。

## 7. Flutter Web 可以用吗？

可以，但要额外关注三件事：

- 目标服务是否允许浏览器跨域
- `dart_sm` 相关依赖在 Web 构建目标下是否可用
- 公钥分发方式是否符合你的安全要求

如果你在 Web 下遇到请求失败，优先排查：

- CORS
- Mixed Content（HTTPS 页面请求 HTTP 接口）
- 浏览器控制台里的网络错误

## 8. 这个包支持 dio 吗？

支持。

当前包除了 `TransferEncryptClient` 之外，还提供了 `TransferEncryptDioAdapter`，适合已经统一使用 `dio` 的项目。

示例：

```dart
final adapter = TransferEncryptDioAdapter(
  dio: Dio(BaseOptions(baseUrl: 'http://localhost:8080')),
  publicKey: '服务端 SM2 公钥',
);
```

适合场景：

- 你已有统一的 `dio` 拦截器
- 你希望复用现有超时、代理、证书、日志配置

## 9. `GET/DELETE` 为什么还会携带 `transferPayload`？

因为这个协议会把 query 参数加密后重新拼接到 URL 中。

对 `GET/DELETE + params` 来说，客户端并不是直接把原始参数挂上去，而是：

1. 把原始参数序列化
2. 用随机 `SM4 key` 加密
3. 再把加密后的 `transferPayload` 和 `originalContentType` 放进 query string

所以你在抓包时看到的是协议字段，而不是原始业务参数。

## 10. 为什么示例页里的接口路径是 `/api/json`、`/api/form`、`/api/query`、`/api/export`？

这些路径是为了对齐仓库内的联调示例和测试场景，方便你直接验证：

- JSON 请求
- Form 请求
- Query 请求
- 二进制导出请求

如果你的服务端路径不同，建议直接改示例页里的 `baseUrl` 和对应按钮逻辑，或者在业务项目里按自己的网关路径封装一层。

## 11. 没有 Android 真机时，怎么调试示例？

推荐直接使用 Android Emulator / AVD，不要依赖已经退役的 `Windows Subsystem for Android`。

最小步骤：

```powershell
flutter doctor -v
flutter emulators
emulator -list-avds
emulator -avd flutter_api35
flutter devices
flutter run -d emulator-5554
```

如果本机还没有 AVD，可参考：

- [../README.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\README.md)
- [../example/README.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\example\README.md)

## 12. `flutter doctor -v` 里 Android toolchain 未通过怎么办？

优先检查：

- `ANDROID_HOME`
- `ANDROID_SDK_ROOT`
- `JAVA_HOME`
- Android licenses 是否已接受

常用命令：

```powershell
flutter doctor -v
sdkmanager --licenses
```

如果你是 Windows，本机新装完 SDK 之后记得重开终端或 IDE，让新的环境变量生效。

## 13. Android AVD 已创建，但 `flutter devices` 看不到模拟器怎么办？

通常是以下几类原因：

- 只创建了 AVD，但没有真正启动
- `adb` 不在 PATH
- 模拟器启动失败
- 加速环境不可用

建议顺序排查：

```powershell
emulator -list-avds
emulator -avd flutter_api35
adb devices
flutter devices
emulator -accel-check
```

## 14. 首次 `flutter run` / `flutter build apk` 很慢，正常吗？

正常。

首次构建通常会额外下载或生成：

- Gradle wrapper
- Android build-tools
- CMake
- NDK
- Flutter engine artifacts
- Dart / Gradle / build cache

只要不是长时间卡死，第一次慢通常属于正常现象。

## 15. Gradle 下载失败或握手失败怎么办？

如果你在 Android 构建时看到：

- TLS handshake failed
- Connection reset
- Gradle wrapper download failed

优先考虑是网络问题，而不是 Flutter 代码本身问题。

可从这几个方向排查：

- 当前网络是否能访问 `services.gradle.org`
- 公司代理 / 网关是否拦截
- 是否需要预置 Gradle 缓存
- 是否可以先切换到已缓存的 Gradle 发行版

如果其他平台如 Windows / Web 构建正常，而只有 Android Gradle 下载失败，通常就是构建依赖下载链路问题。
