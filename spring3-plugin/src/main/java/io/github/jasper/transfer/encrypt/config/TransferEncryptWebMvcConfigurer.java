package io.github.jasper.transfer.encrypt.config;

import io.github.jasper.transfer.encrypt.web.TransferMultipartIntegrityInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将传输层相关拦截器挂入 Spring MVC。
 */
public class TransferEncryptWebMvcConfigurer implements WebMvcConfigurer {

    private final TransferMultipartIntegrityInterceptor multipartIntegrityInterceptor;

    /**
     * @param multipartIntegrityInterceptor interceptor that verifies multipart file MD5 values
     */
    public TransferEncryptWebMvcConfigurer(final TransferMultipartIntegrityInterceptor multipartIntegrityInterceptor) {
        this.multipartIntegrityInterceptor = multipartIntegrityInterceptor;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(multipartIntegrityInterceptor);
    }
}
