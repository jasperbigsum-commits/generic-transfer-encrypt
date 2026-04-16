# vendor

此目录已经内置可离线部署的前端加密依赖，不需要再访问外网 CDN。

当前内置文件：

- `sm-crypto.min.js`
- `LICENCE_MIT.sm-crypto`

来源信息：

- 包名：`sm-crypto`
- 版本：`0.3.13`
- 来源仓库：`https://github.com/JuneAndGreen/sm-crypto`
- 许可证：`MIT`

兼容接口：

- `window.smCrypto.sm2.doEncrypt(text, publicKey, cipherMode)`
- `window.smCrypto.sm4.encrypt(text, key, options)`
- `window.smCrypto.sm4.decrypt(cipherText, key, options)`

接入示例：

```html
<script src="./vendor/sm-crypto.min.js"></script>
<script src="./transfer-encrypt.js"></script>
<script>
  TransferEncryptRegisterSmCrypto(window.smCrypto);
</script>
```

如果你们已有内部封装库，也可以不使用该目录，直接在 `new TransferEncryptClient({ smCrypto })` 时注入。
