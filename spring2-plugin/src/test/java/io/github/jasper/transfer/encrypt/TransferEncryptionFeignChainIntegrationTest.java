package io.github.jasper.transfer.encrypt;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.SM2;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.annotation.TransferEncryptedFeignClient;
import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferConstants;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.core.TransferRequestContext;
import io.github.jasper.transfer.encrypt.crypto.DefaultTransferCryptoService;
import io.github.jasper.transfer.encrypt.model.TransferEnvelope;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(classes = TransferEncryptionFeignChainIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferEncryptionFeignChainIntegrationTest {

    private static final SM2 TEST_SM2 = SmUtil.sm2();

    private static final SM2 WRONG_SM2 = SmUtil.sm2();

    private static final String PRIVATE_KEY = HexUtil.encodeHexStr(TEST_SM2.getPrivateKey().getEncoded());

    private static final String PUBLIC_KEY = HexUtil.encodeHexStr(TEST_SM2.getPublicKey().getEncoded());

    private static final int TEST_PORT = findAvailablePort();

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(final DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> TEST_PORT);
        registry.add("test.server.url", () -> "http://localhost:" + TEST_PORT);
        registry.add("transfer.encrypt.private-key", () -> PRIVATE_KEY);
        registry.add("transfer.encrypt.public-key", () -> HexUtil.encodeHexStr(WRONG_SM2.getPublicKey().getEncoded()));
        registry.add("transfer.encrypt.include-path-regex[0]", () -> "^/gateway/.*$");
        registry.add("transfer.encrypt.include-path-regex[1]", () -> "^/downstream/.*$");
        registry.add("transfer.encrypt.feign-enabled", () -> "true");
        registry.add("transfer.encrypt.feign-public-keys.localhost", () -> HexUtil.encodeHexStr(WRONG_SM2.getPublicKey().getEncoded()));
        registry.add("transfer.encrypt.feign-public-keys.downstream-wrong", () -> HexUtil.encodeHexStr(WRONG_SM2.getPublicKey().getEncoded()));
        registry.add("transfer.encrypt.feign-public-keys.downstream-correct", () -> PUBLIC_KEY);
    }

    @Test
    void shouldCompleteThreeHopFlowWithAnnotatedFeignClient() throws Exception {
        final Map<String, Object> requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("name", "triple-hop");
        final String sm4Key = codec().randomSm4Key();
        final TransferEnvelope requestEnvelope = codec().createRequestEnvelope(
                objectMapper.writeValueAsString(requestBody), MediaType.APPLICATION_JSON_VALUE, sm4Key);

        final ResponseEntity<byte[]> response = restTemplate().postForEntity(
                baseUrl("/gateway/encrypted"),
                jsonEntity(objectMapper.writeValueAsBytes(requestEnvelope)),
                byte[].class);

        Assertions.assertEquals(200, response.getStatusCodeValue());
        Assertions.assertEquals("true", response.getHeaders().getFirst(TransferConstants.HEADER_TRANSFER_ENCRYPTED));
        final Map<?, ?> gatewayResponse = decryptJsonResponse(response.getBody(), sm4Key, Map.class);
        final Map<?, ?> downstream = (Map<?, ?>) gatewayResponse.get("downstream");
        Assertions.assertEquals("triple-hop", downstream.get("name"));
        Assertions.assertEquals(Boolean.TRUE, downstream.get("encryptedRequest"));
    }

    @Test
    void shouldKeepPlainFeignClientUnwrapped() throws Exception {
        final Map<String, Object> requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("name", "plain-hop");
        final String sm4Key = codec().randomSm4Key();
        final TransferEnvelope requestEnvelope = codec().createRequestEnvelope(
                objectMapper.writeValueAsString(requestBody), MediaType.APPLICATION_JSON_VALUE, sm4Key);

        final ResponseEntity<byte[]> response = restTemplate().postForEntity(
                baseUrl("/gateway/plain"),
                jsonEntity(objectMapper.writeValueAsBytes(requestEnvelope)),
                byte[].class);

        Assertions.assertEquals(200, response.getStatusCodeValue());
        final Map<?, ?> gatewayResponse = decryptJsonResponse(response.getBody(), sm4Key, Map.class);
        final Map<?, ?> downstream = (Map<?, ?>) gatewayResponse.get("downstream");
        Assertions.assertEquals("plain-hop", downstream.get("name"));
        Assertions.assertEquals(Boolean.FALSE, downstream.get("encryptedRequest"));
    }

    @Test
    void shouldAllowMethodLevelMd5Disabled() throws Exception {
        final Map<String, Object> requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("name", "md5-disabled");
        final String sm4Key = codec().randomSm4Key();
        final TransferEnvelope requestEnvelope = codec().createRequestEnvelope(
                objectMapper.writeValueAsString(requestBody), MediaType.APPLICATION_JSON_VALUE, sm4Key);

        final ResponseEntity<byte[]> response = restTemplate().postForEntity(
                baseUrl("/gateway/md5-disabled"),
                jsonEntity(objectMapper.writeValueAsBytes(requestEnvelope)),
                byte[].class);

        Assertions.assertEquals(200, response.getStatusCodeValue());
        final Map<?, ?> gatewayResponse = decryptJsonResponse(response.getBody(), sm4Key, Map.class);
        Assertions.assertEquals("wrong-md5-body", gatewayResponse.get("body"));
    }

    @Test
    void shouldRejectWrongMd5WhenMethodLevelMd5Enabled() throws Exception {
        final Map<String, Object> requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("name", "md5-enabled");
        final String sm4Key = codec().randomSm4Key();
        final TransferEnvelope requestEnvelope = codec().createRequestEnvelope(
                objectMapper.writeValueAsString(requestBody), MediaType.APPLICATION_JSON_VALUE, sm4Key);

        final HttpServerErrorException exception = Assertions.assertThrows(HttpServerErrorException.class, () ->
                restTemplate().postForEntity(
                        baseUrl("/gateway/md5-enabled"),
                        jsonEntity(objectMapper.writeValueAsBytes(requestEnvelope)),
                        byte[].class));
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
    }

    private HttpEntity<byte[]> jsonEntity(final byte[] body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<byte[]>(body, headers);
    }

    private String baseUrl(final String path) {
        return "http://localhost:" + TEST_PORT + path;
    }

    private RestTemplate restTemplate() {
        return new RestTemplate();
    }

    private <T> T decryptJsonResponse(final byte[] responseBody, final String sm4Key, final Class<T> type)
            throws Exception {
        final TransferEnvelope responseEnvelope = objectMapper.readValue(responseBody, TransferEnvelope.class);
        final byte[] plaintext = codec().decodeResponseEnvelope(responseEnvelope, sm4Key);
        return objectMapper.readValue(plaintext, type);
    }

    private TransferEnvelopeCodec codec() {
        final TransferEncryptProperties properties = new TransferEncryptProperties();
        properties.setPrivateKey(PRIVATE_KEY);
        properties.setPublicKey(PUBLIC_KEY);
        return new TransferEnvelopeCodec(new DefaultTransferCryptoService(properties));
    }

    private static int findAvailablePort() {
        try {
            final ServerSocket socket = new ServerSocket(0);
            try {
                return socket.getLocalPort();
            } finally {
                socket.close();
            }
        } catch (final IOException ex) {
            throw new IllegalStateException("无法分配测试端口", ex);
        }
    }

    @SpringBootApplication
    @EnableFeignClients(clients = {EncryptedBridgeClient.class, PlainBridgeClient.class})
    @Import({GatewayController.class, DownstreamController.class})
    static class TestApplication {
    }

    @TransferEncryptedFeignClient(publicKeyAlias = "downstream-wrong", md5Enabled = true)
    @FeignClient(name = "encryptedBridgeClient", url = "${test.server.url}")
    interface EncryptedBridgeClient {

        @TransferEncryptedFeignClient(publicKeyAlias = "downstream-correct")
        @PostMapping(value = "/downstream/encrypted", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, Object> relay(@RequestBody Map<String, Object> requestBody);

        @TransferEncryptedFeignClient(publicKeyAlias = "downstream-correct", md5Enabled = false)
        @GetMapping(value = "/downstream/md5-disabled", produces = MediaType.TEXT_PLAIN_VALUE)
        String md5Disabled();

        @GetMapping(value = "/downstream/md5-enabled", produces = MediaType.TEXT_PLAIN_VALUE)
        String md5Enabled();
    }

    @FeignClient(name = "plainBridgeClient", url = "${test.server.url}")
    interface PlainBridgeClient {

        @PostMapping(value = "/downstream/plain", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, Object> relay(@RequestBody Map<String, Object> requestBody);
    }

    @RestController
    @RequestMapping("/gateway")
    static class GatewayController {

        private final EncryptedBridgeClient encryptedBridgeClient;

        private final PlainBridgeClient plainBridgeClient;

        GatewayController(final EncryptedBridgeClient encryptedBridgeClient,
                final PlainBridgeClient plainBridgeClient) {
            this.encryptedBridgeClient = encryptedBridgeClient;
            this.plainBridgeClient = plainBridgeClient;
        }

        @PostMapping(value = "/encrypted", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> encrypted(@RequestBody final Map<String, Object> requestBody) {
            return Collections.<String, Object>singletonMap("downstream", encryptedBridgeClient.relay(requestBody));
        }

        @PostMapping(value = "/plain", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> plain(@RequestBody final Map<String, Object> requestBody) {
            return Collections.<String, Object>singletonMap("downstream", plainBridgeClient.relay(requestBody));
        }

        @PostMapping(value = "/md5-disabled", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> md5Disabled(@RequestBody final Map<String, Object> requestBody) {
            return Collections.<String, Object>singletonMap("body", encryptedBridgeClient.md5Disabled());
        }

        @PostMapping(value = "/md5-enabled", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> md5Enabled(@RequestBody final Map<String, Object> requestBody) {
            return Collections.<String, Object>singletonMap("body", encryptedBridgeClient.md5Enabled());
        }
    }

    @RestController
    @RequestMapping("/downstream")
    static class DownstreamController {

        @PostMapping(value = "/encrypted", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> encrypted(@RequestBody final Map<String, Object> requestBody,
                final HttpServletRequest request) {
            return response(requestBody, request);
        }

        @PostMapping(value = "/plain", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> plain(@RequestBody final Map<String, Object> requestBody,
                final HttpServletRequest request) {
            return response(requestBody, request);
        }

        @GetMapping(value = "/md5-disabled", produces = MediaType.TEXT_PLAIN_VALUE)
        public ResponseEntity<String> md5Disabled() {
            return ResponseEntity.ok()
                    .header(TransferConstants.HEADER_CONTENT_MD5, "wrong-md5")
                    .body("wrong-md5-body");
        }

        @GetMapping(value = "/md5-enabled", produces = MediaType.TEXT_PLAIN_VALUE)
        public ResponseEntity<String> md5Enabled() {
            return ResponseEntity.ok()
                    .header(TransferConstants.HEADER_CONTENT_MD5, "wrong-md5")
                    .body("wrong-md5-body");
        }

        private Map<String, Object> response(final Map<String, Object> requestBody, final HttpServletRequest request) {
            final Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("name", requestBody.get("name"));
            final TransferRequestContext context =
                    (TransferRequestContext) request.getAttribute(TransferConstants.REQUEST_ATTRIBUTE);
            response.put("encryptedRequest", context != null && context.isEncryptedRequest());
            return response;
        }
    }
}
