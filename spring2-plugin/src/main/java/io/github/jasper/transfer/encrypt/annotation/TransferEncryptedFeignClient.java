package io.github.jasper.transfer.encrypt.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要启用传输层加密的 OpenFeign 接口或方法。
 *
 * <p>支持两类可选配置：</p>
 * <ul>
 *     <li>{@code publicKeyAlias}：指定下游服务公钥别名</li>
 *     <li>{@code md5Enabled}：控制是否启用额外的 MD5 头校验</li>
 * </ul>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TransferEncryptedFeignClient {

    /**
     * Declares the downstream public-key alias to use for the current Feign interface or method.
     *
     * @return the alias in {@code transfer.encrypt.feign-public-keys}, or an empty string to fall back to host or global resolution
     */
    String publicKeyAlias() default "";

    /**
     * Controls whether an extra {@code X-Transfer-Content-MD5} header should be added and verified.
     *
     * @return {@code true} to enable the additional MD5 header, {@code false} to only rely on the envelope-internal checksum
     */
    boolean md5Enabled() default true;
}
