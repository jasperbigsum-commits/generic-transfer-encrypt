package io.github.jasper.transfer.encrypt;

import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.crypto.DefaultTransferCryptoService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest(classes = TransferMultipartEmbeddedServerIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferMultipartEmbeddedServerIntegrationTest {

    private static final Sm2TestKeySupport.Sm2KeyPair TEST_KEY_PAIR = Sm2TestKeySupport.generateKeyPair();

    private static final String PRIVATE_KEY = TEST_KEY_PAIR.getPrivateKeyHex();

    private static final String PUBLIC_KEY = TEST_KEY_PAIR.getPublicKeyHex();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void registerProperties(final DynamicPropertyRegistry registry) {
        registry.add("transfer.encrypt.private-key", () -> PRIVATE_KEY);
        registry.add("transfer.encrypt.public-key", () -> PUBLIC_KEY);
        registry.add("transfer.encrypt.include-path-regex[0]", () -> "^/api/.*$");
    }

    @Test
    void shouldKeepMultipartFilePartAvailableAfterTransferFilter() {
        final byte[] fileBytes = "upload-content".getBytes(StandardCharsets.UTF_8);
        final MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();
        body.add("file", namedBytes("demo.txt", fileBytes));
        body.add("__md5_file", codec().md5Hex(fileBytes));

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        final ResponseEntity<Map> response = restTemplate.postForEntity("http://localhost:" + port + "/api/upload",
                new HttpEntity<MultiValueMap<String, Object>>(body, headers), Map.class);

        Assertions.assertEquals(200, response.getStatusCodeValue());
        Assertions.assertEquals("demo.txt", response.getBody().get("fileName"));
        Assertions.assertEquals(14, response.getBody().get("size"));
    }

    private ByteArrayResource namedBytes(final String fileName, final byte[] content) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private TransferEnvelopeCodec codec() {
        final TransferEncryptProperties properties = new TransferEncryptProperties();
        properties.setPrivateKey(PRIVATE_KEY);
        properties.setPublicKey(PUBLIC_KEY);
        return new TransferEnvelopeCodec(new DefaultTransferCryptoService(properties));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(DemoController.class)
    static class TestApplication {
    }

    @RestController
    @RequestMapping("/api")
    static class DemoController {

        @PostMapping(value = "/upload", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> upload(@RequestPart("file") final MultipartFile file) throws IOException {
            final Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("fileName", file.getOriginalFilename());
            response.put("size", file.getSize());
            response.put("content", new String(file.getBytes(), StandardCharsets.UTF_8));
            return response;
        }
    }
}
