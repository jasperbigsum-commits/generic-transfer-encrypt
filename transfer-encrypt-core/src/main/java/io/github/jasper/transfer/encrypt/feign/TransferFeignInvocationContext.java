package io.github.jasper.transfer.encrypt.feign;

/**
 * 当前线程的 Feign 调用上下文。
 *
 * <p>由于底层 {@code feign.Client} 只知道“正在发请求”，并不知道是哪个接口/方法触发的，
 * 因此需要通过 ThreadLocal 把方法级配置透传到真正发请求的位置。</p>
 */
public final class TransferFeignInvocationContext {

    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<Boolean>();

    private static final ThreadLocal<String> PUBLIC_KEY_ALIAS = new ThreadLocal<String>();

    private static final ThreadLocal<Boolean> MD5_ENABLED = new ThreadLocal<Boolean>();

    private TransferFeignInvocationContext() {
    }

    public static void enable(final String publicKeyAlias, final boolean md5Enabled) {
        ENABLED.set(Boolean.TRUE);
        MD5_ENABLED.set(Boolean.valueOf(md5Enabled));
        if (publicKeyAlias != null) {
            PUBLIC_KEY_ALIAS.set(publicKeyAlias);
        }
    }

    public static void clear() {
        ENABLED.remove();
        PUBLIC_KEY_ALIAS.remove();
        MD5_ENABLED.remove();
    }

    public static boolean isEnabled() {
        return Boolean.TRUE.equals(ENABLED.get());
    }

    public static String getPublicKeyAlias() {
        return PUBLIC_KEY_ALIAS.get();
    }

    public static boolean isMd5Enabled() {
        return !Boolean.FALSE.equals(MD5_ENABLED.get());
    }
}
