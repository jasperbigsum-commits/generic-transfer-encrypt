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

## 启动

推荐直接用根目录脚本：

```powershell
.\scripts\run-e2e-demo.ps1
```

默认地址：

- 首页：`http://localhost:8081/`
- 公钥接口：`http://localhost:8081/demo/public-key`

## 两类测试模式

当前 E2E 提供两类模式：

### 1. 手动联调版

适合前端、Flutter、浏览器页面人工验证：

```powershell
.\scripts\run-e2e-demo.ps1
```

这条脚本会：

1. 先把 `spring2-plugin` 安装到本地 Maven 仓库
2. 再对 `e2e-demo/server` 执行 `spring-boot:run`
3. 你再手工打开浏览器页面联调

### 2. 自动探针版

适合快速回归：

```powershell
.\scripts\run-e2e-cross-stack.ps1
```

这条脚本会：

1. 安装 `spring2-plugin`
2. 对 `e2e-demo/server` 执行 `package`
3. 启动 fat jar
4. 用 `e2e-demo/tests/client-cross-stack-probe.mjs` 执行自动校验

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

并继续保留两类测试入口：

- 手动联调版
- 自动探针版

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
