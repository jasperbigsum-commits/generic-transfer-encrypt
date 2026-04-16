package io.github.jasper.transfer.encrypt.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Client;
import feign.Feign;
import io.github.jasper.transfer.encrypt.annotation.TransferEncryptedFeignClient;
import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.crypto.TransferCryptoService;
import io.github.jasper.transfer.encrypt.web.TransferPathMatcher;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * OpenFeign 选择性加密接管器。
 *
 * <p>之所以使用 BeanPostProcessor，而不是只包一层 Feign 接口代理，是因为
 * Spring Cloud OpenFeign 内部还会持有自己的 {@code Client} / {@code MethodHandler}，
 * 需要把这些真正发请求的位置也替换成加密包装器。</p>
 */
public class TransferFeignBeanPostProcessor implements BeanPostProcessor, Ordered {

    private final TransferEnvelopeCodec envelopeCodec;

    private final ObjectMapper objectMapper;

    private final TransferPathMatcher pathMatcher;

    private final TransferEncryptProperties properties;

    private final TransferCryptoService cryptoService;

    public TransferFeignBeanPostProcessor(final TransferEnvelopeCodec envelopeCodec, final ObjectMapper objectMapper,
            final TransferPathMatcher pathMatcher, final TransferEncryptProperties properties,
            final TransferCryptoService cryptoService) {
        this.envelopeCodec = envelopeCodec;
        this.objectMapper = objectMapper;
        this.pathMatcher = pathMatcher;
        this.properties = properties;
        this.cryptoService = cryptoService;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        if (!properties.isFeignEnabled() || cryptoService == null) {
            return bean;
        }
        if (bean instanceof Feign.Builder) {
            wrapBuilderClient((Feign.Builder) bean);
            return bean;
        }
        if (bean instanceof Client && !(bean instanceof TransferFeignClientWrapper)) {
            return new TransferFeignClientWrapper((Client) bean, envelopeCodec, objectMapper, pathMatcher, properties);
        }
        if (hasTransferEncryptedFeignInterface(bean)) {
            if (Proxy.isProxyClass(bean.getClass())
                    && Proxy.getInvocationHandler(bean) instanceof TransferFeignInvocationHandler) {
                return bean;
            }
            wrapFeignProxyClient(bean);
            final Class<?>[] interfaces = ClassUtils.getAllInterfaces(bean);
            if (interfaces.length == 0) {
                return bean;
            }
            return Proxy.newProxyInstance(bean.getClass().getClassLoader(), interfaces,
                    new TransferFeignInvocationHandler(bean, resolveTypeLevelPublicKeyAlias(bean),
                            resolveTypeLevelMd5Enabled(bean)));
        }
        return bean;
    }

    private boolean hasTransferEncryptedFeignInterface(final Object bean) {
        for (final Class<?> type : ClassUtils.getAllInterfaces(bean)) {
            if (AnnotationUtils.findAnnotation(type, TransferEncryptedFeignClient.class) != null) {
                return true;
            }
        }
        return false;
    }

    private String resolveTypeLevelPublicKeyAlias(final Object bean) {
        for (final Class<?> type : ClassUtils.getAllInterfaces(bean)) {
            final TransferEncryptedFeignClient annotation =
                    AnnotationUtils.findAnnotation(type, TransferEncryptedFeignClient.class);
            if (annotation != null && annotation.publicKeyAlias() != null
                    && !annotation.publicKeyAlias().trim().isEmpty()) {
                return annotation.publicKeyAlias().trim();
            }
        }
        return null;
    }

    private boolean resolveTypeLevelMd5Enabled(final Object bean) {
        for (final Class<?> type : ClassUtils.getAllInterfaces(bean)) {
            final TransferEncryptedFeignClient annotation =
                    AnnotationUtils.findAnnotation(type, TransferEncryptedFeignClient.class);
            if (annotation != null) {
                return annotation.md5Enabled();
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void wrapFeignProxyClient(final Object bean) {
        if (!Proxy.isProxyClass(bean.getClass())) {
            return;
        }
        // Spring Cloud OpenFeign 最终会把每个方法映射到独立的 MethodHandler，这里替换其内部 Client。
        final InvocationHandler handler = Proxy.getInvocationHandler(bean);
        final Field dispatchField = ReflectionUtils.findField(handler.getClass(), "dispatch");
        if (dispatchField == null) {
            return;
        }
        ReflectionUtils.makeAccessible(dispatchField);
        final Object dispatchObject = ReflectionUtils.getField(dispatchField, handler);
        if (!(dispatchObject instanceof Map)) {
            return;
        }
        final Map<Method, Object> dispatch = (Map<Method, Object>) dispatchObject;
        for (final Object methodHandler : dispatch.values()) {
            final Field clientField = ReflectionUtils.findField(methodHandler.getClass(), "client");
            if (clientField == null) {
                continue;
            }
            ReflectionUtils.makeAccessible(clientField);
            final Object clientObject = ReflectionUtils.getField(clientField, methodHandler);
            if (clientObject instanceof Client && !(clientObject instanceof TransferFeignClientWrapper)) {
                ReflectionUtils.setField(clientField, methodHandler,
                        new TransferFeignClientWrapper((Client) clientObject, envelopeCodec, objectMapper, pathMatcher,
                                properties));
            }
        }
    }

    private void wrapBuilderClient(final Feign.Builder builder) {
        final Field clientField = ReflectionUtils.findField(builder.getClass(), "client");
        if (clientField == null) {
            return;
        }
        ReflectionUtils.makeAccessible(clientField);
        final Object fieldValue = ReflectionUtils.getField(clientField, builder);
        if (fieldValue instanceof Client && !(fieldValue instanceof TransferFeignClientWrapper)) {
            ReflectionUtils.setField(clientField, builder, new TransferFeignClientWrapper((Client) fieldValue,
                    envelopeCodec, objectMapper, pathMatcher, properties));
        }
    }

    private static final class TransferFeignInvocationHandler implements InvocationHandler {

        private final Object target;

        private final String typeLevelPublicKeyAlias;

        private final boolean typeLevelMd5Enabled;

        private TransferFeignInvocationHandler(final Object target, final String typeLevelPublicKeyAlias,
                final boolean typeLevelMd5Enabled) {
            this.target = target;
            this.typeLevelPublicKeyAlias = typeLevelPublicKeyAlias;
            this.typeLevelMd5Enabled = typeLevelMd5Enabled;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(target, args);
            }
            final Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            ReflectionUtils.makeAccessible(targetMethod);
            // 当前 Feign 调用需要使用哪个公钥别名，通过 ThreadLocal 透传到底层 Client 包装器。
            TransferFeignInvocationContext.enable(resolveMethodLevelAlias(method), resolveMethodLevelMd5Enabled(method));
            try {
                return targetMethod.invoke(target, args);
            } catch (final java.lang.reflect.InvocationTargetException ex) {
                throw ex.getTargetException();
            } finally {
                TransferFeignInvocationContext.clear();
            }
        }

        private String resolveMethodLevelAlias(final Method method) {
            final TransferEncryptedFeignClient methodAnnotation =
                    AnnotationUtils.findAnnotation(method, TransferEncryptedFeignClient.class);
            if (methodAnnotation != null && methodAnnotation.publicKeyAlias() != null
                    && !methodAnnotation.publicKeyAlias().trim().isEmpty()) {
                // 方法级别优先级最高，可覆盖接口上的默认别名。
                return methodAnnotation.publicKeyAlias().trim();
            }
            return typeLevelPublicKeyAlias;
        }

        private boolean resolveMethodLevelMd5Enabled(final Method method) {
            final TransferEncryptedFeignClient methodAnnotation =
                    AnnotationUtils.findAnnotation(method, TransferEncryptedFeignClient.class);
            if (methodAnnotation != null) {
                return methodAnnotation.md5Enabled();
            }
            return typeLevelMd5Enabled;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
