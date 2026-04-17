package io.github.jasper.transfer.encrypt.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferConstants;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.crypto.DefaultTransferCryptoService;
import io.github.jasper.transfer.encrypt.model.TransferEnvelope;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        classes = E2EDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18081",
                "demo.feign-base-url=http://localhost:18081"
        })
class E2EDemoIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransferEncryptProperties properties;

    @Test
    void shouldExposeBrowserCompatiblePublicKey() {
        final ResponseEntity<Map> response = restTemplate.getForEntity("http://localhost:18081/demo/public-key", Map.class);
        Assertions.assertEquals(200, response.getStatusCodeValue());
        final Object publicKey = response.getBody().get("publicKey");
        Assertions.assertTrue(String.valueOf(publicKey).matches("^04[0-9a-fA-F]{128}$"));
    }

    @Test
    void shouldCompleteEncryptedJsonRoundTrip() throws Exception {
        final String sm4Key = codec().randomSm4Key();
        final Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", "alice");
        final TransferEnvelope envelope = codec().createRequestEnvelope(
                objectMapper.writeValueAsString(body),
                MediaType.APPLICATION_JSON_VALUE,
                sm4Key);

        final ResponseEntity<byte[]> response = restTemplate.postForEntity(
                "http://localhost:18081/api/json",
                jsonEntity(encryptedJsonRequest(envelope, MediaType.APPLICATION_JSON_VALUE)),
                byte[].class);

        Assertions.assertEquals(200, response.getStatusCodeValue());
        Assertions.assertEquals("true", response.getHeaders().getFirst(TransferConstants.HEADER_TRANSFER_ENCRYPTED));
        final Map<?, ?> payload = decryptJsonResponse(response.getBody(), sm4Key);
        Assertions.assertEquals("alice", ((Map<?, ?>) payload.get("received")).get("name"));
    }

    @Test
    void shouldDecodeEnvelopeGeneratedByBrowserSmCrypto() {
        final TransferEnvelope envelope = new TransferEnvelope();
        envelope.setEncryptedKey(
                "652ca9ebe60fef654cc0dc08341ee64aeb63b78777f217f8568a95cbaeb647ae"
                        + "ae6d9e1d600dd01f2667bfde8fa473abbe9f64ef0d6352f84b9ef277ec51c2d5"
                        + "ef3850445df598a0c61defa5e10f3fec431a60fcc19339c8952071f88eeed033"
                        + "ec58371fc1a10510f0ddee1af3e8a060");
        envelope.setEncryptedData(
                "94ccf1d73aa84fb4519fbf2dda74b13bdaed86aa8077b0c9ce0d9513da10f175"
                        + "c7f6595628418db9df7db2c0199cfdad");
        envelope.setContentMd5("955e77c2a9855f6cefcf4640310d0e67");

        final DefaultTransferCryptoService cryptoService = new DefaultTransferCryptoService(properties);
        final String sm4Key = cryptoService.decryptSm2Key(envelope.getEncryptedKey());
        Assertions.assertEquals("1234567890abcdef", sm4Key);

        final String plaintext = cryptoService.decryptBySm4(envelope.getEncryptedData(), sm4Key);
        Assertions.assertEquals("{\"name\":\"probe-json\",\"from\":\"node-probe\"}", plaintext);
    }

    @Test
    void shouldDecodeEnvelopeGeneratedByBrowserSmCryptoWithRandomAsciiKey() {
        final TransferEnvelope envelope = new TransferEnvelope();
        envelope.setEncryptedKey(
                "eb238d0a68bcbd97af452ac794c8f3cf43b211a1e1d10c44314e9890e5dd684a"
                        + "60af7807963774d60939a7c764535be7c513306799491dd8daa82baca48ce941"
                        + "f95d8a7d4a80702025fc8c17ea4bc73af6ac2fa0ef9c20a170720ebc9936fd03"
                        + "2f73cb512a875bd6917646e13a658131");
        envelope.setEncryptedData(
                "cb03817b65223fca18e5fe4204e8ed874e9c4c0c14089ee08b6f09a01cf38c3c"
                        + "a186579a9ea7148ab67daaee94b99ff9");
        envelope.setContentMd5("955e77c2a9855f6cefcf4640310d0e67");

        final DefaultTransferCryptoService cryptoService = new DefaultTransferCryptoService(properties);
        final String sm4Key = cryptoService.decryptSm2Key(envelope.getEncryptedKey());
        Assertions.assertEquals("4NwXcow3DIarYEI1", sm4Key);

        final String plaintext = cryptoService.decryptBySm4(envelope.getEncryptedData(), sm4Key);
        Assertions.assertEquals("{\"name\":\"probe-json\",\"from\":\"node-probe\"}", plaintext);
    }

    @Test
    void shouldDecodeEnvelopeCapturedFromLiveBrowserClientRequest() {
        final TransferEnvelope envelope = new TransferEnvelope();
        envelope.setEncryptedKey(
                "a881271cd887b50757de167520e9a3afb035dbf104bb750576537081e1c3a66d"
                        + "6752b77d2de27dc67e79737f17827e643c4a104124a9e33b48fe31068ec1b1db"
                        + "9c823c6d488daec8f16d8dfc65bf247afc22eabcd61aa7db08a35307badc613f"
                        + "24cd895ca9f29c57087dbdae895a2c00");
        envelope.setEncryptedData(
                "1ecc0a34072eb1bec71b0be7d21d832db32a0a5d974b9af304090ceb53882e5f"
                        + "8f86b46600725d9679f2fb484d6f6097");
        envelope.setContentMd5("955e77c2a9855f6cefcf4640310d0e67");

        final DefaultTransferCryptoService cryptoService = new DefaultTransferCryptoService(properties);
        final String sm4Key = cryptoService.decryptSm2Key(envelope.getEncryptedKey());
        final String plaintext = cryptoService.decryptBySm4(envelope.getEncryptedData(), sm4Key);
        Assertions.assertEquals("{\"name\":\"probe-json\",\"from\":\"node-probe\"}", plaintext);
    }

    @Test
    void shouldCompleteEncryptedBrowserToFeignToDownstreamRoundTrip() throws Exception {
        final String sm4Key = codec().randomSm4Key();
        final Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("name", "alice-feign");
        body.put("from", "browser-test");
        final TransferEnvelope envelope = codec().createRequestEnvelope(
                objectMapper.writeValueAsString(body),
                MediaType.APPLICATION_JSON_VALUE,
                sm4Key);

        final ResponseEntity<byte[]> response = restTemplate.postForEntity(
                "http://localhost:18081/api/feign/json",
                jsonEntity(encryptedJsonRequest(envelope, MediaType.APPLICATION_JSON_VALUE)),
                byte[].class);

        Assertions.assertEquals(200, response.getStatusCodeValue());
        Assertions.assertEquals("true", response.getHeaders().getFirst(TransferConstants.HEADER_TRANSFER_ENCRYPTED));
        final Map<?, ?> payload = decryptJsonResponse(response.getBody(), sm4Key);
        final Map<?, ?> downstream = (Map<?, ?>) payload.get("downstream");
        Assertions.assertEquals("alice-feign", downstream.get("name"));
        Assertions.assertEquals(Boolean.TRUE, downstream.get("encryptedRequest"));
    }


    private HttpEntity<byte[]> jsonEntity(final byte[] body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<byte[]>(body, headers);
    }

    private Map<?, ?> decryptJsonResponse(final byte[] responseBody, final String sm4Key) throws Exception {
        final Map<?, ?> wrapper = objectMapper.readValue(responseBody, Map.class);
        final TransferEnvelope responseEnvelope = objectMapper.readValue(
                java.util.Base64.getUrlDecoder().decode(padBase64(String.valueOf(wrapper.get("transferPayload")))),
                TransferEnvelope.class);
        final byte[] plaintext = codec().decodeResponseEnvelope(responseEnvelope, sm4Key);
        return objectMapper.readValue(plaintext, Map.class);
    }

    private byte[] encryptedJsonRequest(final TransferEnvelope envelope, final String originalContentType) throws Exception {
        final Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
        wrapper.put(TransferConstants.FIELD_TRANSFER_PAYLOAD,
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(envelope)));
        wrapper.put(TransferConstants.FIELD_ORIGINAL_CONTENT_TYPE, originalContentType);
        return objectMapper.writeValueAsBytes(wrapper);
    }

    private String padBase64(final String value) {
        final int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        final StringBuilder builder = new StringBuilder(value);
        for (int index = mod; index < 4; index++) {
            builder.append('=');
        }
        return builder.toString();
    }

    private TransferEnvelopeCodec codec() {
        return new TransferEnvelopeCodec(new DefaultTransferCryptoService(properties));
    }
}
