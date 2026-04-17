# vue3-plugin

Vue 3 侧传输层加密 SDK，协议与仓库中的 `spring2-plugin`、`vanilla-js-plugin`、`flutter-plugin` 保持一致。

设计目标：

- 对齐现有加密信封协议，不再发明新字段
- 兼容 Vue 3 `plugin / provide/inject / composable`
- 不强制你引入 `axios`
- 文件上传继续复用 `FormData + __md5_<fieldName>` 规则
- 保持轻量，默认无构建依赖

## 提供内容

- `transfer-encrypt-vue3-core.js`
  纯协议核心，适合测试或非 Vue 场景直接复用
- `transfer-encrypt-vue3.js`
  Vue 3 适配层，提供 `app.use(...)`、`useTransferEncrypt()` 等接口

## 安装

如果你在当前仓库内联调：

```json
{
  "dependencies": {
    "generic-transfer-encrypt-vue3": "file:../vue3-plugin"
  }
}
```

要求：

- `vue@^3`
- 本地可用的 `SM2/SM4` 实现

公钥格式说明：

- 如果传入的是 `128` 位十六进制裸点公钥，SDK 会自动补成 `04 + X + Y`
- 如果已经是 `130` 位未压缩公钥点，SDK 会直接使用
- 如果传入的是 `DER/X509/PEM`，SDK 会直接报明确错误

## 本地国密实现注入

你需要自行提供 `smCrypto` 兼容对象，接口要求与 `vanilla-js-plugin` 一致：

- `sm2.doEncrypt(text, publicKey, cipherMode)`
- `sm4.encrypt(text, key, options)`
- `sm4.decrypt(cipherText, key, options)`

例如：

```js
import smCrypto from 'sm-crypto'
```

或你们自己的封装对象。

## 方式一：直接使用核心客户端

```js
import { createTransferEncryptClient } from 'generic-transfer-encrypt-vue3/core'

const client = createTransferEncryptClient({
  baseUrl: 'http://localhost:8080',
  publicKey: '服务端SM2公钥',
  smCrypto,
  fetchImpl: window.fetch.bind(window)
})

const result = await client.request({
  url: '/api/json',
  method: 'POST',
  json: { name: 'alice' }
})
```

## 方式二：作为 Vue Plugin 注入

```js
import { createApp } from 'vue'
import App from './App.vue'
import { createTransferEncryptPlugin } from 'generic-transfer-encrypt-vue3'

const app = createApp(App)

app.use(createTransferEncryptPlugin({
  baseUrl: 'http://localhost:8080',
  publicKey: '服务端SM2公钥',
  smCrypto,
  fetchImpl: window.fetch.bind(window)
}))

app.mount('#app')
```

组件内使用：

```vue
<script setup>
import { useTransferEncrypt } from 'generic-transfer-encrypt-vue3'

const client = useTransferEncrypt()

async function submit() {
  const result = await client.request({
    url: '/api/form',
    method: 'POST',
    form: { name: 'alice' }
  })
  console.log(result)
}
</script>
```

## 方式三：使用 composable 请求状态

```vue
<script setup>
import { useTransferEncryptRequest } from 'generic-transfer-encrypt-vue3'

const { loading, data, error, execute } = useTransferEncryptRequest(() => ({
  url: '/api/query',
  method: 'GET',
  params: { name: 'bob' }
}))
</script>
```

## 上传文件

```js
import { useTransferEncryptAdapter } from 'generic-transfer-encrypt-vue3'

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

说明：

- 单文件自动追加 `__md5_<fieldName>`
- 多文件自动追加 `__md5_<fieldName>__<index>`
- 文件内容默认不加密

## 下载文件

```js
const arrayBuffer = await client.request({
  url: '/api/file',
  method: 'GET',
  responseType: 'arrayBuffer'
})
```

## API

核心导出：

- `TransferEncryptClient`
- `createTransferEncryptClient(options)`
- `createTransferEncryptVueAdapter(options)`

Vue 3 导出：

- `createTransferEncryptPlugin(options)`
- `provideTransferEncrypt(input)`
- `useTransferEncrypt()`
- `useTransferEncryptAdapter()`
- `useTransferEncryptRequest(source)`

## 示例

参考：

- [example/main.js](./example/main.js)
- [example/App.vue](./example/App.vue)
- [example/index.html](./example/index.html)
- [example/README.md](./example/README.md)

补充文档：

- [docs/vue3-plugin-guide.md](./docs/vue3-plugin-guide.md)
- [docs/vue3-plugin-api.md](./docs/vue3-plugin-api.md)
- [docs/vue3-plugin-faq.md](./docs/vue3-plugin-faq.md)

## 本地测试

```powershell
cd .\vue3-plugin
node .\tests\transfer-encrypt-vue3.native.test.mjs
```
