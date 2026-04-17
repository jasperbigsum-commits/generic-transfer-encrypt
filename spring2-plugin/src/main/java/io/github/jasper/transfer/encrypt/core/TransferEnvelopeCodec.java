package io.github.jasper.transfer.encrypt.core;

import io.github.jasper.transfer.encrypt.crypto.TransferCryptoService;
import io.github.jasper.transfer.encrypt.model.TransferDecodedPayload;
import io.github.jasper.transfer.encrypt.model.TransferEnvelope;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StringUtils;

/**
 * 统一处理加密信封的编解码。
 *
 * <p>这里把协议细节从 Web / Feign 层抽离出来，保证前后端与服务间调用复用同一套规则。</p>
 */
public class TransferEnvelopeCodec {

    private final TransferCryptoService cryptoService;

    public TransferEnvelopeCodec(final TransferCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public TransferEnvelope createRequestEnvelope(final String plaintext, final String originalContentType) {
        final String sm4Key = cryptoService.randomSm4Key();
        return createRequestEnvelope(plaintext, originalContentType, sm4Key);
    }

    public TransferEnvelope createRequestEnvelope(final String plaintext, final String originalContentType,
            final String sm4Key) {
        return createRequestEnvelope(plaintext, originalContentType, sm4Key, null);
    }

    public TransferEnvelope createRequestEnvelope(final String plaintext, final String originalContentType,
            final String sm4Key, final String publicKey) {
        final TransferEnvelope envelope = new TransferEnvelope();
        envelope.setEncryptedKey(cryptoService.encryptSm2Key(sm4Key, publicKey));
        envelope.setEncryptedData(cryptoService.encryptBySm4(plaintext, sm4Key));
        envelope.setContentMd5(cryptoService.md5Hex(plaintext.getBytes(StandardCharsets.UTF_8)));
        envelope.setTimestamp(System.currentTimeMillis());
        return envelope;
    }

    public TransferDecodedPayload decodeRequestEnvelope(final TransferEnvelope envelope,
            final String originalContentType) {
        // 先解出本次请求的 SM4 key，再用该 key 解业务明文。
        final String sm4Key = cryptoService.decryptSm2Key(envelope.getEncryptedKey());
        final String plaintext = cryptoService.decryptBySm4(envelope.getEncryptedData(), sm4Key);
        // 校验完整性
        verifyMd5(plaintext.getBytes(StandardCharsets.UTF_8), envelope.getContentMd5());
        return new TransferDecodedPayload(plaintext, sm4Key, originalContentType);
    }

    public TransferEnvelope createResponseEnvelope(final byte[] plaintext, final String originalContentType,
            final String sm4Key) {
        final TransferEnvelope envelope = new TransferEnvelope();
        envelope.setEncryptedData(cryptoService.encryptBySm4(new String(plaintext, StandardCharsets.UTF_8), sm4Key));
        envelope.setContentMd5(cryptoService.md5Hex(plaintext));
        envelope.setTimestamp(System.currentTimeMillis());
        return envelope;
    }

    public byte[] decodeResponseEnvelope(final TransferEnvelope envelope, final String sm4Key) {
        final String plaintext = cryptoService.decryptBySm4(envelope.getEncryptedData(), sm4Key);
        final byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
        verifyMd5(bytes, envelope.getContentMd5());
        return bytes;
    }

    public void verifyMd5(final byte[] content, final String md5) {
        if (StringUtils.hasText(md5) && !md5.equalsIgnoreCase(cryptoService.md5Hex(content))) {
            throw new TransferException("MD5 完整性校验失败");
        }
    }

    public String md5Hex(final byte[] content) {
        return cryptoService.md5Hex(content);
    }

    public String randomSm4Key() {
        return cryptoService.randomSm4Key();
    }
}
