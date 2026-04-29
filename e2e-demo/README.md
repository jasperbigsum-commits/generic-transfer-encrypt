# E2E Demo

本目录提供一个本地端到端联调示例，目标是把：

- `spring2-plugin`
- `vanilla-js-plugin`
- 浏览器端联调页面

串成一条真正可手动验证的链路。

## 目录

- `server/`: Spring Boot 演示服务，启用了传输层加密插件

## 功能

演示页面支持：

- 加密 JSON 请求
- 加密表单请求
- 加密 query 请求
- 单文件上传
- 多文件上传
- 二进制下载
- Layui `$.ajax(...)` 自动加密桥接
- Layui `table.render({ url })` 自动加密桥接
- Layui `form.on('submit') + table.reload()` 联动查询
- Layui `renderForm(...)` 自动回填并执行 `form.render()`
- 浏览器入站加密 + OpenFeign 出站加密 + 下游再次解密的端到端链路

默认地址：

- 首页：`http://localhost:8081/`
- 公钥接口：`http://localhost:8081/demo/public-key`


## 当前版本边界

当前仓库的 E2E 服务只有：

- `server/`: Spring Boot 2 + `generic-transfer-encrypt-spring2-plugin`

后续如果补 Spring Boot 3 版本，建议目录并列扩展为：

```text
e2e-demo/
├─ server-spring2/
├─ server-spring3/
└─ tests/
```

## 手动启动

先安装本地 Spring 插件到同一个 Maven 本地仓库：

```powershell
cd .\spring2-plugin
mvn "-Dmaven.repo.local=.m2repo" install
```

再启动 demo 服务：

```powershell
cd .\e2e-demo\server
mvn "-Dmaven.repo.local=..\..\spring2-plugin\.m2repo" spring-boot:run
```

## 说明

- 演示服务会在启动时生成一套临时 `SM2` 密钥对
- 页面会通过 `/demo/public-key` 自动拉取服务端公钥
- 该目录主要用于本地联调，不是正式发布工程模板
