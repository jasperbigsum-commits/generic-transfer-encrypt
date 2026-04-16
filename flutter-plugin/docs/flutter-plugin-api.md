# Flutter Plugin API 说明

## TransferEncryptClient

构造函数：

```dart
TransferEncryptClient({
  required String publicKey,
  String baseUrl = '',
  http.Client? httpClient,
  String multipartMd5FieldPrefix = '__md5_',
  Random? random,
})
```

### request

```dart
Future<dynamic> request({
  required String url,
  String method = 'GET',
  Map<String, String>? headers,
  Map<String, dynamic>? params,
  Map<String, dynamic>? form,
  Object? json,
})
```

用途：

- 通用明文结果请求

注意：

- `params` 仅建议与 `GET/DELETE` 组合
- `form/json` 二选一

### postJson / postForm / get / delete

上层便捷方法，内部仍然复用 `request`。

### upload

```dart
Future<dynamic> upload(
  String url, {
  required List<TransferEncryptFile> files,
  Map<String, dynamic>? fields,
  Map<String, String>? headers,
  String method = 'POST',
})
```

用途：

- `multipart/form-data` 文件上传

行为：

- 自动追加 MD5 字段
- 不加密文件字节

### uploadSingle

```dart
Future<dynamic> uploadSingle(
  String url, {
  required TransferEncryptFile file,
  Map<String, dynamic>? fields,
  Map<String, String>? headers,
  String method = 'POST',
})
```

### requestBinary / download

```dart
Future<TransferEncryptBinaryResponse> requestBinary({
  required String url,
  String method = 'GET',
  Map<String, String>? headers,
  Map<String, dynamic>? params,
  Map<String, dynamic>? form,
  Object? json,
})
```

用途：

- 面向文件导出、字节流下载

行为：

- 若头里包含 `X-Transfer-Content-MD5`，自动校验

## TransferEncryptFile

构造函数：

```dart
TransferEncryptFile({
  required String fieldName,
  required List<int> bytes,
  required String filename,
  MediaType? contentType,
})
```

便捷工厂：

```dart
TransferEncryptFile.fromText(...)
```

属性：

- `fieldName`
- `bytes`
- `filename`
- `contentType`
- `md5Hex`

## TransferEncryptBinaryResponse

属性：

- `bytes`
- `statusCode`
- `headers`
- `contentType`
- `contentMd5`

## 异常

### TransferEncryptException

协议错误、MD5 校验失败、配置缺失等异常。

### TransferEncryptHttpException

HTTP 非 2xx 返回时抛出，额外包含：

- `statusCode`
- `responseBody`
- `headers`
