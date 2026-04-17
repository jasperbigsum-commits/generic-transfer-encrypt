# Flutter Plugin 接入指南

本文说明 `generic_transfer_encrypt_flutter` 如何和本仓库 Spring 插件配套使用。

## 1. 适用场景

适合以下调用场景：

- Flutter App 调用 Spring Boot 2 服务端加密接口
- Flutter 桌面端调用内网服务
- Flutter Web 调用已启用传输层加密的 HTTP 接口
- 业务 JSON / form / query 需要按仓库现有协议封装

不适合的场景：

- 需要替代 HTTPS / mTLS
- 需要对大文件内容本身做流式加密
- 需要服务端无状态地解密历史响应

## 2. 与 Spring 协议的对应关系

请求时：

1. 客户端生成 16 位随机 `SM4 key`
2. 明文按 `UTF-8` 编码后计算 `MD5`
3. 用 `SM4 key` 加密明文
4. 用服务端 `SM2 publicKey` 加密 `SM4 key`
5. 提交信封字段：
   `algorithm`
   `encryptedKey`
   `encryptedData`
   `contentMd5`
   `originalContentType`
   `timestamp`

响应时：

1. 服务端复用本次请求的 `SM4 key`
2. 返回加密响应信封
3. 客户端用同一次请求的 `SM4 key` 解密
4. 客户端再次校验 `contentMd5`

文件上传时：

- 文件内容默认不加密
- 单文件字段追加 `__md5_<fieldName>`
- 多文件字段追加 `__md5_<fieldName>__<index>`

文件下载时：

- 文件内容默认不解密
- 若响应头存在 `X-Transfer-Content-MD5`，客户端会自动校验

## 3. 依赖安装

本仓库内联调：

```yaml
dependencies:
  generic_transfer_encrypt_flutter:
    path: ../flutter-plugin
```

独立项目使用时建议：

1. 将包发布到内部 pub 仓库
2. 或直接以 Git/path 方式引用
3. 保证与服务端协议版本同步

## 4. 初始化方式

```dart
final client = TransferEncryptClient(
  baseUrl: 'http://localhost:8080',
  publicKey: '服务端 SM2 公钥',
);
```

参数说明：

- `baseUrl`: 服务端根地址，可为空
- `publicKey`: 服务端 `SM2` 公钥
- `multipartMd5FieldPrefix`: 默认 `__md5_`
- `httpClient`: 可传入自定义 `package:http` client

如果你的工程以 `dio` 为标准 HTTP 栈，也可以改用：

```dart
final adapter = TransferEncryptDioAdapter(
  dio: Dio(BaseOptions(baseUrl: 'http://localhost:8080')),
  publicKey: '服务端 SM2 公钥',
);
```

建议：

- 在应用层做成单例
- 登录态、租户头、traceId 由业务层在请求 headers 注入
- 如果你已有统一网关域名，直接把 `baseUrl` 指向网关即可

## 5. 发送请求

### 5.1 JSON

```dart
final result = await client.postJson(
  '/api/order/create',
  body: <String, dynamic>{
    'orderNo': 'SO-10001',
    'amount': 128,
  },
);
```

### 5.2 Form

```dart
final result = await client.postForm(
  '/api/form/submit',
  body: <String, dynamic>{
    'name': 'alice',
    'age': 18,
  },
);
```

### 5.3 Query

```dart
final result = await client.get(
  '/api/order/query',
  params: <String, dynamic>{
    'orderNo': 'SO-10001',
  },
);
```

### 5.4 通用请求

```dart
final result = await client.request(
  url: '/api/order/update',
  method: 'PUT',
  json: <String, dynamic>{'status': 'DONE'},
  headers: <String, String>{'Authorization': 'Bearer xxx'},
);
```

规则：

- `GET/DELETE + params` 会走加密 query
- `form` 会走加密 `application/x-www-form-urlencoded`
- `json` 会走加密 `application/json`
- 三者不要同时传，避免语义冲突

### 5.5 Dio 接入

```dart
final adapter = TransferEncryptDioAdapter(
  dio: Dio(BaseOptions(baseUrl: 'http://localhost:8080')),
  publicKey: '服务端 SM2 公钥',
);

final result = await adapter.request(
  url: '/api/order/create',
  method: 'POST',
  json: <String, dynamic>{'orderNo': 'SO-10001'},
);
```

适合：

- 你已经统一使用 `dio`
- 你希望复用现有 `dio` 拦截器和基础配置

## 6. 上传文件

### 6.1 单文件

```dart
final result = await client.uploadSingle(
  '/api/upload',
  file: TransferEncryptFile(
    fieldName: 'file',
    filename: 'avatar.png',
    bytes: imageBytes,
  ),
  fields: <String, dynamic>{'bizType': 'avatar'},
);
```

### 6.2 多文件

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

若使用 `dio`：

```dart
final result = await adapter.upload(
  url: '/api/upload/multi',
  files: <TransferEncryptFile>[
    TransferEncryptFile(
      fieldName: 'files',
      filename: 'a.txt',
      bytes: utf8.encode('A'),
    ),
  ],
);
```

### 6.3 文本转文件

```dart
final file = TransferEncryptFile.fromText(
  fieldName: 'file',
  filename: 'note.txt',
  text: 'hello',
);
```

## 7. 下载文件

### 7.1 普通下载

```dart
final binary = await client.download('/api/export');
```

### 7.2 带请求体的二进制接口

```dart
final binary = await client.requestBinary(
  url: '/api/export',
  method: 'POST',
  json: <String, dynamic>{'bizId': 1001},
);
```

若使用 `dio`：

```dart
final binary = await adapter.requestBinary(
  url: '/api/export',
  method: 'POST',
  json: <String, dynamic>{'bizId': 1001},
);
```

返回对象包含：

- `bytes`
- `statusCode`
- `headers`
- `contentType`
- `contentMd5`

## 8. 错误处理

```dart
try {
  await client.postJson('/api/order/create', body: {'id': 1});
} on TransferEncryptHttpException catch (error) {
  final statusCode = error.statusCode;
  final body = error.responseBody;
}
```

说明：

- 若响应是加密 JSON 错误体，会先解密，再作为 `responseBody` 暴露
- 若响应是普通 JSON，也会自动解析
- 若下载类接口返回 JSON 错误体，会优先解析后放入 `responseBody`
- 若下载类接口返回的不是 JSON，异常里会保留原始二进制 body

## 9. 与业务代码集成建议

建议封装业务网关层，而不是在页面直接使用：

```dart
class OrderGateway {
  OrderGateway(this._client);

  final TransferEncryptClient _client;

  Future<Map<String, dynamic>> createOrder(Map<String, dynamic> payload) async {
    final response = await _client.postJson('/api/order/create', body: payload);
    return Map<String, dynamic>.from(response as Map);
  }
}
```

这样做的好处：

- 页面不感知加密协议
- header 注入点统一
- 重试、日志、埋点更容易治理

## 10. 联调检查清单

客户端：

- 是否拿到正确的服务端公钥
- `baseUrl` 是否正确
- JSON / form / query 是否选对模式
- 上传字段名是否与后端 `MultipartFile` 参数一致

服务端：

- `transfer.encrypt.enabled=true`
- `private-key/public-key` 是否成对
- 路径是否命中 `include-path-regex`
- 文件上传接口是否开启了 `multipart` MD5 校验

## 11. 已知边界

- 当前实现基于 `package:http`
- 已内置 `dio` 适配器
- 不负责公钥分发
- 不替代 HTTPS
- 文件内容默认不做流式加密
