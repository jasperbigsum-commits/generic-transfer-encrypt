# Vue 3 Plugin API

## Core

### `TransferEncryptClient`

构造参数：

```js
new TransferEncryptClient({
  baseUrl,
  publicKey,
  smCrypto,
  fetchImpl,
  cryptoImpl
})
```

主要方法：

- `request(options)`
- `encryptPayload(plaintext, originalContentType, sm4Key)`
- `decryptEnvelope(envelope, sm4Key)`

### `createTransferEncryptClient(options)`

返回 `TransferEncryptClient` 实例。

### `createTransferEncryptVueAdapter(options)`

返回适合页面直接调用的适配器对象，主要方法：

- `request(options)`
- `json(url, data, options)`
- `form(url, data, options)`
- `get(url, params, options)`
- `upload(file, options)`
- `uploadMany(files, options)`

## Vue 3

### `createTransferEncryptPlugin(options)`

返回可传给 `app.use(...)` 的插件对象。

插件会注入：

- `$transferEncrypt`
- `$transferEncryptAdapter`

### `provideTransferEncrypt(input)`

在局部组件树中注入 client 和 adapter。

### `useTransferEncrypt()`

读取注入的 `TransferEncryptClient`。

### `useTransferEncryptAdapter()`

读取注入的 adapter。

### `useTransferEncryptRequest(source)`

返回：

- `loading`
- `data`
- `error`
- `execute(overrideOptions)`

## 工具函数

core 额外导出：

- `md5ArrayBuffer`
- `md5String`
- `normalizeParams`
- `appendQuery`
- `ensureMultipartMd5`
