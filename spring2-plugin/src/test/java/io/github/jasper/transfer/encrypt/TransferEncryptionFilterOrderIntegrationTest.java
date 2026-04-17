package io.github.jasper.transfer.encrypt;

import io.github.jasper.transfer.encrypt.web.TransferEncryptionFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = TransferEncryptionFilterOrderIntegrationTest.TestApplication.class)
class TransferEncryptionFilterOrderIntegrationTest {

    @Autowired
    @Qualifier("transferEncryptionFilterRegistrationBean")
    private FilterRegistrationBean<TransferEncryptionFilter> registrationBean;

    @DynamicPropertySource
    static void registerProperties(final DynamicPropertyRegistry registry) {
        registry.add("transfer.encrypt.private-key", () -> "00");
        registry.add("transfer.encrypt.public-key", () -> "00");
        registry.add("transfer.encrypt.filter-order", () -> "-100");
    }

    @Test
    void shouldUseConfiguredFilterOrder() {
        Assertions.assertEquals(-100, registrationBean.getOrder());
    }

    @SpringBootApplication
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
