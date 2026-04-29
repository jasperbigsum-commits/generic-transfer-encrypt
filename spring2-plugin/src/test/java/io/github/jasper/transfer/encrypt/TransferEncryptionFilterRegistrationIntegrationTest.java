package io.github.jasper.transfer.encrypt;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferConstants;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.crypto.DefaultTransferCryptoService;
import io.github.jasper.transfer.encrypt.model.TransferEnvelope;
import io.github.jasper.transfer.encrypt.util.TransferJsonUtils;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TransferEncryptionFilterRegistrationIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class TransferEncryptionFilterRegistrationIntegrationTest {

    private static final Sm2TestKeySupport.Sm2KeyPair TEST_KEY_PAIR = Sm2TestKeySupport.generateKeyPair();

    private static final String PRIVATE_KEY = TEST_KEY_PAIR.getPrivateKeyHex();

    private static final String PUBLIC_KEY = TEST_KEY_PAIR.getPublicKeyHex();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(final DynamicPropertyRegistry registry) {
        registry.add("transfer.encrypt.private-key", () -> PRIVATE_KEY);
        registry.add("transfer.encrypt.public-key", () -> PUBLIC_KEY);
        registry.add("transfer.encrypt.include-path-regex[0]", () -> "^/api/.*$");
    }

    @Test
    void shouldStillRegisterTransferFilterWhenBusinessDefinesAnotherFilterRegistrationBean() throws Exception {
        final Map<String, Object> requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("name", "alice");
        final String plaintext = objectMapper.writeValueAsString(requestBody);
        final String sm4Key = codec().randomSm4Key();
        final TransferEnvelope envelope = codec().createRequestEnvelope(plaintext, MediaType.APPLICATION_JSON_VALUE,
                sm4Key);

        final MvcResult result = mockMvc.perform(post("/api/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrapEnvelope(envelope)))
                .andExpect(status().isOk())
                .andExpect(header().string(TransferConstants.HEADER_TRANSFER_ENCRYPTED, "true"))
                .andReturn();

        final TransferEnvelope responseEnvelope =
                unwrapEnvelope(result.getResponse().getContentAsByteArray());
        final byte[] responsePlaintext = codec().decodeResponseEnvelope(responseEnvelope, sm4Key);
        final Map<?, ?> responseBody = objectMapper.readValue(responsePlaintext, Map.class);
        Assertions.assertEquals("alice", responseBody.get("echo"));
        Assertions.assertEquals("true", result.getResponse().getHeader("X-Business-Filter"));
    }

    private TransferEnvelopeCodec codec() {
        final TransferEncryptProperties properties = new TransferEncryptProperties();
        properties.setPrivateKey(PRIVATE_KEY);
        properties.setPublicKey(PUBLIC_KEY);
        return new TransferEnvelopeCodec(new DefaultTransferCryptoService(properties));
    }

    private byte[] wrapEnvelope(final TransferEnvelope envelope) throws Exception {
        final Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
        wrapper.put(TransferConstants.FIELD_TRANSFER_PAYLOAD,
                TransferJsonUtils.encodeTransportPayload(objectMapper, envelope));
        wrapper.put(TransferConstants.FIELD_ORIGINAL_CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return objectMapper.writeValueAsBytes(wrapper);
    }

    private TransferEnvelope unwrapEnvelope(final byte[] responseBody) throws Exception {
        final Map<?, ?> wrapper = objectMapper.readValue(responseBody, Map.class);
        return TransferJsonUtils.decodeTransportPayload(objectMapper,
                String.valueOf(wrapper.get(TransferConstants.FIELD_TRANSFER_PAYLOAD)));
    }

    @SpringBootApplication
    @EnableAutoConfiguration
    @Import(DemoController.class)
    static class TestApplication {

        @Bean
        public FilterRegistrationBean<Filter> businessFilterRegistrationBean() {
            final FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
            bean.setFilter(new Filter() {
                @Override
                public void doFilter(final ServletRequest request, final ServletResponse response,
                        final FilterChain chain) throws IOException, ServletException {
                    chain.doFilter(request, response);
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    if (response instanceof javax.servlet.http.HttpServletResponse) {
                        ((javax.servlet.http.HttpServletResponse) response).setHeader("X-Business-Filter", "true");
                    }
                }
            });
            bean.setOrder(-5);
            return bean;
        }
    }

    @RestController
    @RequestMapping("/api")
    static class DemoController {

        @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> json(@RequestBody final Map<String, Object> requestBody) {
            return Collections.singletonMap("echo", String.valueOf(requestBody.get("name")));
        }
    }
}
