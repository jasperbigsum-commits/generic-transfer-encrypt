package io.github.jasper.transfer.encrypt;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.SM2;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferConstants;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.crypto.DefaultTransferCryptoService;
import io.github.jasper.transfer.encrypt.model.TransferEnvelope;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TransferEncryptionIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
class TransferEncryptionIntegrationTest {

    private static final SM2 TEST_SM2 = SmUtil.sm2();

    private static final String PRIVATE_KEY = HexUtil.encodeHexStr(TEST_SM2.getPrivateKey().getEncoded());

    private static final String PUBLIC_KEY = HexUtil.encodeHexStr(TEST_SM2.getPublicKey().getEncoded());

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
    void shouldDecryptJsonRequestAndEncryptJsonResponse() throws Exception {
        final Map<String, Object> requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("name", "alice");
        final String plaintext = objectMapper.writeValueAsString(requestBody);
        final String sm4Key = codec().randomSm4Key();
        final TransferEnvelope envelope = codec().createRequestEnvelope(plaintext, MediaType.APPLICATION_JSON_VALUE,
                sm4Key);

        final MvcResult result = mockMvc.perform(post("/api/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(envelope)))
                .andExpect(status().isOk())
                .andExpect(header().string(TransferConstants.HEADER_TRANSFER_ENCRYPTED, "true"))
                .andReturn();

        final Map<?, ?> responseBody = decryptJsonResponse(result, sm4Key, Map.class);
        Assertions.assertEquals("alice", responseBody.get("echo"));
    }

    @Test
    void shouldDecryptQueryParameters() throws Exception {
        final String plaintext = "name=bob";
        final String sm4Key = codec().randomSm4Key();
        final TransferEnvelope envelope = codec().createRequestEnvelope(plaintext,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE, sm4Key);
        final String queryString = buildEnvelopeQuery(envelope);

        final MvcResult result = mockMvc.perform(get("/api/query?" + queryString))
                .andExpect(status().isOk())
                .andExpect(header().string(TransferConstants.HEADER_TRANSFER_ENCRYPTED, "true"))
                .andReturn();

        final Map<?, ?> responseBody = decryptJsonResponse(result, sm4Key, Map.class);
        Assertions.assertEquals("bob", responseBody.get("name"));
    }

    @Test
    void shouldDecryptFormRequestWithoutBreakingBinding() throws Exception {
        final String plaintext = "name=carol";
        final String sm4Key = codec().randomSm4Key();
        final TransferEnvelope envelope = codec().createRequestEnvelope(plaintext,
                MediaType.APPLICATION_FORM_URLENCODED_VALUE, sm4Key);

        final MvcResult result = mockMvc.perform(post("/api/form")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(buildEnvelopeQuery(envelope)))
                .andExpect(status().isOk())
                .andExpect(header().string(TransferConstants.HEADER_TRANSFER_ENCRYPTED, "true"))
                .andReturn();

        final Map<?, ?> responseBody = decryptJsonResponse(result, sm4Key, Map.class);
        Assertions.assertEquals("carol", responseBody.get("name"));
    }

    @Test
    void shouldAppendMd5ForBinaryResponse() throws Exception {
        final MvcResult result = mockMvc.perform(get("/api/file"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TransferConstants.HEADER_CONTENT_MD5))
                .andReturn();

        final byte[] responseBody = result.getResponse().getContentAsByteArray();
        Assertions.assertEquals(codec().md5Hex(responseBody),
                result.getResponse().getHeader(TransferConstants.HEADER_CONTENT_MD5));
    }

    @Test
    void shouldVerifyMultipartMd5() throws Exception {
        final byte[] fileBytes = "upload-content".getBytes(StandardCharsets.UTF_8);
        final MockMultipartFile file = new MockMultipartFile("file", "demo.txt", MediaType.TEXT_PLAIN_VALUE, fileBytes);

        mockMvc.perform(multipart("/api/upload")
                        .file(file)
                        .param("__md5_file", codec().md5Hex(fileBytes)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldVerifyMultipartMd5ForMultipleFilesWithSameField() throws Exception {
        final byte[] firstFileBytes = "upload-content-1".getBytes(StandardCharsets.UTF_8);
        final byte[] secondFileBytes = "upload-content-2".getBytes(StandardCharsets.UTF_8);
        final MockMultipartFile firstFile =
                new MockMultipartFile("files", "demo1.txt", MediaType.TEXT_PLAIN_VALUE, firstFileBytes);
        final MockMultipartFile secondFile =
                new MockMultipartFile("files", "demo2.txt", MediaType.TEXT_PLAIN_VALUE, secondFileBytes);

        mockMvc.perform(multipart("/api/upload/multi")
                        .file(firstFile)
                        .file(secondFile)
                        .param("__md5_files__0", codec().md5Hex(firstFileBytes))
                        .param("__md5_files__1", codec().md5Hex(secondFileBytes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    private <T> T decryptJsonResponse(final MvcResult result, final String sm4Key, final Class<T> type)
            throws Exception {
        final TransferEnvelope responseEnvelope =
                objectMapper.readValue(result.getResponse().getContentAsByteArray(), TransferEnvelope.class);
        final byte[] plaintext = codec().decodeResponseEnvelope(responseEnvelope, sm4Key);
        return objectMapper.readValue(plaintext, type);
    }

    private String buildEnvelopeQuery(final TransferEnvelope envelope) {
        return TransferConstants.FIELD_ENCRYPTED_KEY + "=" + urlEncode(envelope.getEncryptedKey()) + "&"
                + TransferConstants.FIELD_ENCRYPTED_DATA + "=" + urlEncode(envelope.getEncryptedData()) + "&"
                + TransferConstants.FIELD_CONTENT_MD5 + "=" + urlEncode(envelope.getContentMd5());
    }

    private String urlEncode(final String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private TransferEnvelopeCodec codec() {
        final TransferEncryptProperties properties = new TransferEncryptProperties();
        properties.setPrivateKey(PRIVATE_KEY);
        properties.setPublicKey(PUBLIC_KEY);
        return new TransferEnvelopeCodec(new DefaultTransferCryptoService(properties));
    }

    @SpringBootApplication
    @EnableAutoConfiguration
    @Import(DemoController.class)
    static class TestApplication {
    }

    @RestController
    @RequestMapping("/api")
    static class DemoController {

        @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> json(@RequestBody final Map<String, Object> requestBody) {
            return Collections.singletonMap("echo", String.valueOf(requestBody.get("name")));
        }

        @GetMapping(value = "/query", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> query(@RequestParam("name") final String name) {
            return Collections.singletonMap("name", name);
        }

        @PostMapping(value = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> form(@RequestParam("name") final String name) {
            return Collections.singletonMap("name", name);
        }

        @GetMapping(value = "/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
        public byte[] file() {
            return "file-download".getBytes(StandardCharsets.UTF_8);
        }

        @PostMapping(value = "/upload", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> upload(@RequestParam("file") final MultipartFile file) {
            return Collections.singletonMap("fileName", file.getOriginalFilename());
        }

        @PostMapping(value = "/upload/multi", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> uploadMulti(@RequestParam("files") final MultipartFile[] files) {
            return Collections.<String, Object>singletonMap("count", files.length);
        }
    }
}
