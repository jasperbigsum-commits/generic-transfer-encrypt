# Vue 3 Example

这个目录只提供最小接入示例，展示：

- 如何通过 `createTransferEncryptPlugin(...)` 注入客户端
- 如何在 `App.vue` 里使用 `useTransferEncryptRequest(...)`
- 如何发起与仓库协议一致的加密 GET 请求

接入前提：

1. 你的工程已安装 `vue@3`
2. 你的页面或构建产物中可提供 `smCrypto`
3. 服务端已启用 `spring2-plugin`

示例文件：

- `main.js`
- `App.vue`
