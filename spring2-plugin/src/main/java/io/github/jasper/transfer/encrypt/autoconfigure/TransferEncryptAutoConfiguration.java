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
import javax.servlet.Filter;
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

    @Bean
    @ConditionalOnMissingBean
    public TransferCryptoService transferCryptoService(final TransferEncryptProperties properties) {
        return new DefaultTransferCryptoService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransferEnvelopeCodec transferEnvelopeCodec(final TransferCryptoService transferCryptoService) {
        return new TransferEnvelopeCodec(transferCryptoService);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransferPathMatcher transferPathMatcher(final TransferEncryptProperties properties) {
        return new TransferPathMatcher(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<Filter> transferEncryptionFilter(final ObjectMapper objectMapper,
            final TransferEnvelopeCodec transferEnvelopeCodec, final TransferPathMatcher transferPathMatcher) {
        final FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TransferEncryptionFilter(objectMapper, transferEnvelopeCodec, transferPathMatcher));
        bean.setOrder(-10);
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean
    public TransferMultipartIntegrityInterceptor transferMultipartIntegrityInterceptor(
            final TransferCryptoService transferCryptoService, final TransferEncryptProperties properties,
            final TransferPathMatcher transferPathMatcher) {
        return new TransferMultipartIntegrityInterceptor(transferCryptoService, properties, transferPathMatcher);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransferEncryptWebMvcConfigurer transferEncryptWebMvcConfigurer(
            final TransferMultipartIntegrityInterceptor multipartIntegrityInterceptor) {
        return new TransferEncryptWebMvcConfigurer(multipartIntegrityInterceptor);
    }

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
