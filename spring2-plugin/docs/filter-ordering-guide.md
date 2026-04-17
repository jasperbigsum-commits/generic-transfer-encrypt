# Filter 顺序指南

本文专门说明业务项目里自定义 `Filter` / `FilterRegistrationBean` 与传输层加解密 filter 的协作方式。

## 1. 典型问题

你可能会遇到这样的现象：

- 业务项目里已经注册了一个或多个 `FilterRegistrationBean`
- Controller 层收不到解密后的参数
- 请求体被提前消费，导致传输层 filter 无法再读取密文
- 返回结果没有被重新加密

这类问题通常不是“过滤器链失效”，而是下面两种情况之一：

1. 旧版本插件因为自动配置退让条件过宽，没有把 `TransferEncryptionFilter` 注册进去
2. 业务 filter 的执行顺序早于传输层 filter，并且它提前读取了 body、包装了 request 或直接短路返回

## 2. 当前版本的处理方式

当前插件已经修复“存在其他 `FilterRegistrationBean` 就不注册”的问题。

现在的注册方式是：

- 单独注册 `TransferEncryptionFilter`
- 单独注册名字固定的 `transferEncryptionFilterRegistrationBean`
- 只在缺少同名注册 Bean 或缺少同类型 filter 时才退让

所以：

- 业务侧存在其他 `FilterRegistrationBean` 不会再导致插件整体失效
- 真正还需要你关注的是顺序问题

## 3. 推荐顺序

建议把 filter 按职责分成三层看：

第一层：应在传输层 filter 之前执行

- TraceId / request-id 生成
- 日志 MDC 初始化
- 极轻量的白名单/黑名单标记

第二层：尽量让传输层 filter 提前执行

- `TransferEncryptionFilter`

第三层：放在传输层 filter 之后执行

- 会读取 request body 的审计 filter
- 会包装 request / response 的业务 filter
- 权限校验里依赖明文参数的 filter
- 业务签名校验 filter

## 4. 什么时候一定要把插件 filter 提前

如果业务 filter 存在以下行为，就必须保证它排在传输层 filter 后面：

- 调用 `request.getInputStream()`
- 调用 `request.getReader()`
- 自己缓存或重建 body
- 改写 parameterMap / queryString
- 直接返回响应，不继续 `chain.doFilter(...)`

否则会出现：

- 传输层 filter 读不到原始密文
- 解密后参数绑定失效
- 返回体无法按同请求 `SM4 key` 再次加密

## 5. 配置方式

插件默认：

```yaml
transfer:
  encrypt:
    filter-order: -10
```

如果业务侧存在更早执行且会消费请求体的 filter，可以把插件 filter 提前：

```yaml
transfer:
  encrypt:
    filter-order: -100
```

经验建议：

- 不确定时先设成 `-100`
- 如果你们项目还有更早的网关式安全 filter，再根据实际链路微调

## 6. 业务侧示例

错误示例：

```java
@Bean
public FilterRegistrationBean<Filter> auditFilter() {
    FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
    bean.setFilter(new AuditBodyReadingFilter());
    bean.setOrder(-200);
    return bean;
}
```

如果这个 `AuditBodyReadingFilter` 会先读取 body，而插件 `filter-order` 还是 `-10`，那么传输层 filter 到达时，请求体很可能已经被业务 filter 消费掉。

更稳的方式有两种：

1. 把业务 filter 放到插件之后

```java
bean.setOrder(0);
```

2. 或者把插件 filter 提前

```yaml
transfer:
  encrypt:
    filter-order: -100
```

## 7. 什么时候需要自定义覆盖注册 Bean

只有在下面场景才建议你自己覆盖插件的注册 Bean：

- 你们项目对所有 filter 顺序有统一编排规范
- 你需要额外设置 URL pattern / dispatcher type
- 你明确要替换默认的 `transferEncryptionFilterRegistrationBean`

示例：

```java
@Bean(name = "transferEncryptionFilterRegistrationBean")
public FilterRegistrationBean<TransferEncryptionFilter> customTransferFilterRegistration(
        TransferEncryptionFilter filter) {
    FilterRegistrationBean<TransferEncryptionFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(filter);
    bean.setOrder(-100);
    return bean;
}
```

## 8. 排查清单

如果你怀疑是顺序冲突，先查这几项：

1. 是否存在业务 filter 先读取 body
2. `transfer.encrypt.filter-order` 当前是多少
3. 业务 filter 的 `order` 是否比插件更小
4. 是否有 filter 在命中后没有继续 `chain.doFilter(...)`
5. Controller 里是否还能拿到明文参数

## 9. 推荐结论

对大多数业务项目，最稳的默认建议是：

- 先保留插件默认注册
- 把 `transfer.encrypt.filter-order` 设为 `-100`
- 所有会读取 body 的业务 filter 放到 `0` 或更大
