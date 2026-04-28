package io.github.jasper.transfer.encrypt.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.config.TransferEncryptWebMvcConfigurer;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.crypto.DefaultTransferCryptoService;
import io.github.jasper.transfer.encrypt.crypto.TransferCryptoService;
import io.github.jasper.transfer.encrypt.feign.TransferFeignBeanPostProcessor;
import io.github.jasper.transfer.encrypt.web.TransferEncryptionFilter;
import io.github.jasper.transfer.encrypt.web.TransferMultipartIntegrityInterceptor;
import io.github.jasper.transfer.encrypt.web.TransferPathMatcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Starter 自动配置入口。
 *
 * <p>职责：</p>
 * <ul>
 *     <li>注册加解密核心服务</li>
 *     <li>挂载 MVC 过滤器与 multipart 校验拦截器</li>
 *     <li>在存在 OpenFeign 时注册选择性加密的接管逻辑</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TransferEncryptProperties.class)
@ConditionalOnProperty(prefix = "transfer.encrypt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransferEncryptAutoConfiguration {

    /**
     * Creates the default crypto implementation backed by the configured SM2 key pair.
     *
     * @param properties externalized starter properties
     * @return the crypto service used by web and Feign adapters
     */
    @Bean
    @ConditionalOnMissingBean
    public TransferCryptoService transferCryptoService(final TransferEncryptProperties properties) {
        return new DefaultTransferCryptoService(properties);
    }

    /**
     * Exposes the shared envelope codec used to encode and decode the transport protocol.
     *
     * @param transferCryptoService the active crypto implementation
     * @return the shared envelope codec
     */
    @Bean
    @ConditionalOnMissingBean
    public TransferEnvelopeCodec transferEnvelopeCodec(final TransferCryptoService transferCryptoService) {
        return new TransferEnvelopeCodec(transferCryptoService);
    }

    /**
     * Compiles include and exclude path regex expressions into a reusable matcher.
     *
     * @param properties externalized starter properties
     * @return the path matcher used by servlet and Feign integrations
     */
    @Bean
    @ConditionalOnMissingBean
    public TransferPathMatcher transferPathMatcher(final TransferEncryptProperties properties) {
        return new TransferPathMatcher(properties);
    }

    /**
     * Registers the transport encryption servlet filter with the configured order.
     *
     * @param objectMapper Jackson object mapper from the host application
     * @param transferEnvelopeCodec shared transport envelope codec
     * @param transferPathMatcher request path matcher
     * @param properties externalized starter properties
     * @return the dedicated registration bean for {@link TransferEncryptionFilter}
     */
    @Bean(name = "transferEncryptionFilterRegistrationBean")
    @ConditionalOnMissingBean(name = "transferEncryptionFilterRegistrationBean")
    public FilterRegistrationBean<TransferEncryptionFilter> transferEncryptionFilterRegistrationBean(
            final ObjectMapper objectMapper, final TransferEnvelopeCodec transferEnvelopeCodec,
            final TransferPathMatcher transferPathMatcher, final TransferEncryptProperties properties) {
        final FilterRegistrationBean<TransferEncryptionFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TransferEncryptionFilter(objectMapper, transferEnvelopeCodec, transferPathMatcher));
        bean.setOrder(properties.getFilterOrder());
        return bean;
    }

    /**
     * Registers multipart MD5 verification for encrypted endpoints.
     *
     * @param transferCryptoService active crypto implementation
     * @param properties externalized starter properties
     * @param transferPathMatcher request path matcher
     * @return interceptor that validates uploaded file integrity
     */
    @Bean
    @ConditionalOnMissingBean
    public TransferMultipartIntegrityInterceptor transferMultipartIntegrityInterceptor(
            final TransferCryptoService transferCryptoService, final TransferEncryptProperties properties,
            final TransferPathMatcher transferPathMatcher) {
        return new TransferMultipartIntegrityInterceptor(transferCryptoService, properties, transferPathMatcher);
    }

    /**
     * Adds the multipart integrity interceptor to Spring MVC.
     *
     * @param multipartIntegrityInterceptor interceptor that validates uploaded file integrity
     * @return MVC configurer used to register the interceptor
     */
    @Bean
    @ConditionalOnMissingBean
    public TransferEncryptWebMvcConfigurer transferEncryptWebMvcConfigurer(
            final TransferMultipartIntegrityInterceptor multipartIntegrityInterceptor) {
        return new TransferEncryptWebMvcConfigurer(multipartIntegrityInterceptor);
    }

    /**
     * Hooks into OpenFeign bean initialization so only annotated clients are transparently wrapped.
     *
     * @param transferEnvelopeCodec shared transport envelope codec
     * @param objectMapper Jackson object mapper from the host application
     * @param transferPathMatcher request path matcher
     * @param properties externalized starter properties
     * @param transferCryptoService optional crypto implementation, present only when the crypto bean is active
     * @return BeanPostProcessor that wraps Feign clients and builders
     */
    @Bean
    @ConditionalOnClass(name = "feign.Client")
    @ConditionalOnProperty(prefix = "transfer.encrypt", name = "feign-enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    public static TransferFeignBeanPostProcessor transferFeignBeanPostProcessor(
            final TransferEnvelopeCodec transferEnvelopeCodec, final ObjectMapper objectMapper,
            final TransferPathMatcher transferPathMatcher, final TransferEncryptProperties properties,
            final ObjectProvider<TransferCryptoService> transferCryptoService) {
        return new TransferFeignBeanPostProcessor(transferEnvelopeCodec, objectMapper, transferPathMatcher,
                properties, transferCryptoService.getIfAvailable());
    }
}
