# Example

该示例页面演示：

- 输入服务端 `baseUrl`
- 输入服务端 `SM2 publicKey`
- 发送加密 JSON 请求
- 发送加密 form 请求
- 发送加密 query 请求
- 发送带请求体的二进制导出请求
- 在页面中查看请求示例和响应结果

运行前请先在本机安装 Flutter，并在 `example/` 目录执行：

```bash
flutter pub get
flutter run
```

默认示例按钮对应的服务端路径：

- `POST /api/json`
- `POST /api/form`
- `GET /api/query`
- `POST /api/export`

如果你正在联调本仓库里的 Spring 示例服务，这四类路径可以直接拿来验证：

- JSON 加密请求
- Form 加密请求
- Query 加密请求
- 二进制导出和 `MD5` 校验

如果没有 Android 真机，可先启动一个 AVD，再指定模拟器运行：

```powershell
flutter emulators
emulator -list-avds
emulator -avd flutter_api35
flutter devices
flutter run -d emulator-5554
```

若本机还没有 AVD，可参考上级文档里的 Android AVD 测试章节：

- [../README.md](E:\IdeaProject\generic-transfer-encrypt\flutter-plugin\README.md)
