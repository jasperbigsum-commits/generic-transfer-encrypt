package io.github.jasper.transfer.encrypt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.core.TransferRequestContext;
import io.github.jasper.transfer.encrypt.crypto.DefaultTransferCryptoService;
import io.github.jasper.transfer.encrypt.util.TransferJsonUtils;
import io.github.jasper.transfer.encrypt.web.TransferWebExchangeProcessor;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TransferWebExchangeProcessorTest {

    private static final Sm2TestKeySupport.Sm2KeyPair KEY_PAIR = Sm2TestKeySupport.generateKeyPair();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateEncryptedResponsePlanInCore() {
        final TransferEnvelopeCodec codec = new TransferEnvelopeCodec(createCryptoService());
        final TransferWebExchangeProcessor processor = new TransferWebExchangeProcessor(objectMapper, codec);
        final String sm4Key = codec.randomSm4Key();

        final TransferWebExchangeProcessor.ResponseResolution resolution = processor.resolveResponse(
                new TransferWebExchangeProcessor.ResponseInput(200,
                        "{\"ok\":true}".getBytes(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_JSON_VALUE, null,
                        new TransferRequestContext(true, true, false, sm4Key)));

        Assertions.assertTrue(resolution.isRewriteBody());
        Assertions.assertEquals(MediaType.APPLICATION_JSON_VALUE, resolution.getContentType());
        Assertions.assertEquals(StandardCharsets.UTF_8.name(), resolution.getCharacterEncoding());
        Assertions.assertEquals("true", resolution.getHeaders().get("X-Transfer-Encrypted"));

        final Map<?, ?> wrapper = TransferJsonUtils.readValue(objectMapper,
                new String(resolution.getBody(), StandardCharsets.UTF_8), Map.class);
        Assertions.assertEquals(MediaType.APPLICATION_JSON_VALUE, wrapper.get("originalContentType"));
        Assertions.assertTrue(wrapper.containsKey("transferPayload"));
    }

    private DefaultTransferCryptoService createCryptoService() {
        final TransferEncryptProperties properties = new TransferEncryptProperties();
        properties.setPrivateKey(KEY_PAIR.getPrivateKeyHex());
        properties.setPublicKey(KEY_PAIR.getPublicKeyHex());
        return new DefaultTransferCryptoService(properties);
    }
}
