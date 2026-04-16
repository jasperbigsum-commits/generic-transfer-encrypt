# vanilla-js-plugin

原生 JS 侧传输层加密 SDK。

SDK 本身不会发起任何外网资源请求，适合内网离线部署。
仓库已内置本地第三方文件：

- [vendor/sm-crypto.min.js](.\example\vendor\sm-crypto.min.js)
- [vendor/LICENCE_MIT.sm-crypto](.\example\vendor\LICENCE_MIT.sm-crypto)

当前 `vanilla-js-plugin` 目录只保留纯静态文件，不依赖 `npm`、`node_modules`、构建工具。

如果你的页面本身使用 `layui`，可以直接接入：

- [layui-example.html](.\example\layui-example.html)
- [layui-upload-example.html](.\example\layui-upload-example.html)
- [layui-upload-multiple-example.html](.\example\layui-upload-multiple-example.html)

## 离线接入方式

需要由你的项目以“本地静态文件”或“内部构建产物”方式提供 `SM2/SM4` 实现，不能再用 CDN。

### 方式一：本地静态文件

直接使用仓库内置的本地文件，或复制到你们自己的静态资源目录：

- `./vendor/sm-crypto.min.js`
- `./transfer-encrypt.js`

页面只引用本地文件：

```html
<script src="./vendor/sm-crypto.min.js"></script>
<script src="./transfer-encrypt.js"></script>
<script>
  TransferEncryptRegisterSmCrypto(window.smCrypto);
</script>
```

### 方式二：内部打包构建注入

如果你们前端工程会把 `sm-crypto` 打进自己的内网构建产物，可以直接在初始化时注入：

```html
<script>
  const client = new TransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: '服务端SM2公钥',
    smCrypto: window.MyLocalSmCrypto
  });
</script>
```

要求只需要兼容以下接口：

- `sm2.doEncrypt(text, publicKey, cipherMode)`
- `sm4.encrypt(text, key, options)`
- `sm4.decrypt(cipherText, key, options)`

## Layui 直连接入

如果页面已经有 `layui`，可以直接使用适配器：

```html
<script src="./vendor/layui/layui.js"></script>
<script src="./vendor/sm-crypto.min.js"></script>
<script src="./transfer-encrypt.js"></script>
<script>
  TransferEncryptRegisterSmCrypto(window.smCrypto);

  layui.use(['form', 'layer'], function () {
    var adapter = TransferEncryptCreateLayuiAdapter({
      layui: layui,
      baseUrl: 'http://localhost:8080',
      publicKey: '服务端SM2公钥'
    });

    adapter.bindFormSubmit('encrypt-submit', {
      url: '/api/form',
      form: true,
      successMessage: '提交成功',
      onSuccess: function (response) {
        layui.layer.alert(JSON.stringify(response));
      }
    });
  });
</script>
```

适配器能力：

- `adapter.request(options)`
- `adapter.json(url, data, options)`
- `adapter.form(url, data, options)`
- `adapter.get(url, params, options)`
- `adapter.upload(file, options)`
- `adapter.uploadMany(files, options)`
- `adapter.bindFormSubmit(filter, config)`

说明：

- 若存在 `layui.layer`，默认会展示加载框与错误提示。
- 适配器仍然复用同一个 `TransferEncryptClient`，协议完全一致。

## Layui Upload 案例

如果你们页面使用 `layui.upload` 选择文件，但上传仍希望走当前插件的 `FormData + 自动 MD5` 链路，可以直接参考：

- [layui-upload-example.html](.\example\layui-upload-example.html)

核心方式是：

1. 用 `layui.upload.render({ auto: false })` 只负责选文件和界面交互。
2. 在自定义按钮点击时调用 `adapter.upload(file, options)`。
3. SDK 会自动把文件追加到 `FormData`，并附带 `__md5_<fieldName>`。

示例：

```html
<script>
  upload.render({
    elem: '#pickFile',
    auto: false,
    choose: function (obj) {
      obj.preview(function (index, file) {
        selectedFile = file;
      });
    }
  });

  document.getElementById('startUpload').addEventListener('click', async function () {
    await adapter.upload(selectedFile, {
      url: '/api/upload',
      fieldName: 'file',
      data: { bizType: 'layui-upload-demo' },
      successMessage: '上传完成'
    });
  });
</script>
```

## Layui Upload 多文件案例

如果页面需要一次上传多个文件，直接参考：

- [layui-upload-multiple-example.html](.\example\layui-upload-multiple-example.html)

核心方式是：

1. 用 `layui.upload.render({ multiple: true, auto: false })` 选择多个文件。
2. 保存所选 `File` 数组。
3. 调用 `adapter.uploadMany(files, options)`。
4. SDK 会自动追加：
   `__md5_<fieldName>__0`
   `__md5_<fieldName>__1`
   `__md5_<fieldName>__2`

示例：

```html
<script>
  upload.render({
    elem: '#pickFiles',
    auto: false,
    multiple: true,
    choose: function (obj) {
      selectedFiles = [];
      obj.preview(function (index, file) {
        selectedFiles.push(file);
      });
    }
  });

  document.getElementById('startMultiUpload').addEventListener('click', async function () {
    await adapter.uploadMany(selectedFiles, {
      url: '/api/upload/multi',
      fieldName: 'files',
      data: { bizType: 'layui-upload-multi-demo' },
      successMessage: '多文件上传完成'
    });
  });
</script>
```

后端规则：

- 单文件字段仍使用 `__md5_<fieldName>`
- 同字段多文件使用 `__md5_<fieldName>__<index>`
- index 从 `0` 开始，顺序与前端追加到 `FormData` 的顺序一致

## 初始化

```html
<script>
  const client = new TransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: '服务端SM2公钥'
  });
</script>
```

## JSON 请求

```html
<script>
  const result = await client.request({
    url: '/api/json',
    method: 'POST',
    json: { name: 'alice' }
  });
</script>
```

## GET / query

```html
<script>
  const result = await client.request({
    url: '/api/query',
    method: 'GET',
    params: { name: 'bob' }
  });
</script>
```

## 表单

```html
<script>
  const result = await client.request({
    url: '/api/form',
    method: 'POST',
    form: { name: 'carol' }
  });
</script>
```

## 文件上传

```html
<script>
  const formData = new FormData();
  formData.append('file', fileInput.files[0]);

 const result = await client.request({
    url: '/api/upload',
    method: 'POST',
    body: formData
  });
</script>
```

说明：

- 文件上传默认不加密。
- SDK 会自动为每个文件字段追加 `__md5_<fieldName>`。
- 同字段多文件时，SDK 会自动追加 `__md5_<fieldName>__<index>`。
- SDK 不依赖外网 CDN；仓库已提供一份本地可用 `sm-crypto` 离线文件。
- 离线示例页面见 [offline-example.html](.\example\offline-example.html)。
