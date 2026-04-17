# Vue 3 Plugin 接入指南

本文说明 `vue3-plugin` 如何和本仓库 Spring 插件配套使用。

## 1. 适用场景

适合：

- Vue 3 SPA 调用启用了传输层加密的 Spring 服务
- 需要以 `plugin / composable` 方式接入现有前端工程
- 仍希望保留浏览器原生 `fetch` 链路，而不是强制切换网络库

不适合：

- 需要替代 HTTPS / mTLS
- 需要大文件内容本身做流式加密
- 需要脱离浏览器环境但又没有 `fetch` 与 `crypto.getRandomValues`

## 2. 协议规则

外层传输字段：

- `transferPayload`
- `originalContentType`

`transferPayload` 内部载荷字段：

- `encryptedKey`
- `encryptedData`
- `contentMd5`
- `timestamp`

文件规则：

- 单文件：`__md5_<fieldName>`
- 多文件：`__md5_<fieldName>__<index>`
- 下载校验头：`X-Transfer-Content-MD5`

## 3. 安装方式

仓库内联调：

```json
{
  "dependencies": {
    "generic-transfer-encrypt-vue3": "file:../vue3-plugin"
  }
}
```

要求：

- `vue@^3`
- 可用的 `smCrypto`
- 浏览器 `fetch`

## 4. 注入国密实现

需要提供兼容对象：

- `sm2.doEncrypt(text, publicKey, cipherMode)`
- `sm4.encrypt(text, key, options)`
- `sm4.decrypt(cipherText, key, options)`

如果你们工程自己打包了 `sm-crypto`，直接在初始化时注入即可。

公钥格式要求：

- 推荐直接传 `04 + X + Y`
- 若只传了 `128` 位裸点，插件会自动补 `04`
- 若服务端给的是 `DER/X509/PEM`，需要先在服务端转换，不能直接喂给浏览器端 SDK

## 5. 三种接入方式

### 5.1 纯 core client

```js
import { createTransferEncryptClient } from 'generic-transfer-encrypt-vue3/core'

const client = createTransferEncryptClient({
  baseUrl: 'http://localhost:8080',
  publicKey: '服务端SM2公钥',
  smCrypto,
  fetchImpl: window.fetch.bind(window)
})
```

### 5.2 Vue Plugin

```js
import { createTransferEncryptPlugin } from 'generic-transfer-encrypt-vue3'

app.use(createTransferEncryptPlugin({
  baseUrl: 'http://localhost:8080',
  publicKey: '服务端SM2公钥',
  smCrypto,
  fetchImpl: window.fetch.bind(window)
}))
```

### 5.3 provide/inject

```js
provideTransferEncrypt({
  baseUrl: 'http://localhost:8080',
  publicKey: '服务端SM2公钥',
  smCrypto,
  fetchImpl: window.fetch.bind(window)
})
```

## 6. 发送请求

### 6.1 JSON

```js
const result = await client.request({
  url: '/api/json',
  method: 'POST',
  json: { name: 'alice' }
})
```

### 6.2 Form

```js
const result = await client.request({
  url: '/api/form',
  method: 'POST',
  form: { name: 'alice' }
})
```

### 6.3 Query

```js
const result = await client.request({
  url: '/api/query',
  method: 'GET',
  params: { name: 'bob' }
})
```

### 6.4 二进制下载

```js
const arrayBuffer = await client.request({
  url: '/api/file',
  method: 'GET',
  responseType: 'arrayBuffer'
})
```

## 7. 上传文件

```js
const adapter = useTransferEncryptAdapter()

await adapter.upload(file, {
  url: '/api/upload',
  fieldName: 'file',
  data: { bizType: 'avatar' }
})
```

多文件：

```js
await adapter.uploadMany(files, {
  url: '/api/upload/multi',
  fieldName: 'files'
})
```

## 8. composable 状态管理

```js
const { loading, data, error, execute } = useTransferEncryptRequest(() => ({
  url: '/api/query',
  method: 'GET',
  params: { name: 'bob' }
}))
```

适合：

- 按钮触发请求
- 页面初始化后再调用
- 自己决定何时执行

## 9. 联调检查清单

客户端：

- `publicKey` 是否正确
- `smCrypto` 是否正确注入
- `fetchImpl` 是否可用
- 上传字段名是否与后端绑定字段一致

服务端：

- `transfer.encrypt.enabled=true`
- 路径命中 `include-path-regex`
- 公私钥成对
- multipart 接口启用了 MD5 校验
