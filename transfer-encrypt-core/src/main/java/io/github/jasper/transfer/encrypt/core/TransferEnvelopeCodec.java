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

    /**
     * @param cryptoService crypto implementation used for SM2/SM4 and MD5 operations
     */
    public TransferEnvelopeCodec(final TransferCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * Creates a request envelope using a freshly generated SM4 key.
     *
     * @param plaintext original request body or query string
     * @param originalContentType original request content type before wrapping
     * @return encoded transport envelope
     */
    public TransferEnvelope createRequestEnvelope(final String plaintext, final String originalContentType) {
        final String sm4Key = cryptoService.randomSm4Key();
        return createRequestEnvelope(plaintext, originalContentType, sm4Key);
    }

    /**
     * Creates a request envelope with a caller-provided SM4 key.
     *
     * @param plaintext original request body or query string
     * @param originalContentType original request content type before wrapping
     * @param sm4Key per-request SM4 key
     * @return encoded transport envelope
     */
    public TransferEnvelope createRequestEnvelope(final String plaintext, final String originalContentType,
            final String sm4Key) {
        return createRequestEnvelope(plaintext, originalContentType, sm4Key, null);
    }

    /**
     * Creates a request envelope and optionally overrides the downstream public key.
     *
     * @param plaintext original request body or query string
     * @param originalContentType original request content type before wrapping
     * @param sm4Key per-request SM4 key
     * @param publicKey downstream SM2 public key override, or {@code null} to use the default key
     * @return encoded transport envelope
     */
    public TransferEnvelope createRequestEnvelope(final String plaintext, final String originalContentType,
            final String sm4Key, final String publicKey) {
        final TransferEnvelope envelope = new TransferEnvelope();
        envelope.setEncryptedKey(cryptoService.encryptSm2Key(sm4Key, publicKey));
        envelope.setEncryptedData(cryptoService.encryptBySm4(plaintext, sm4Key));
        envelope.setContentMd5(cryptoService.md5Hex(plaintext.getBytes(StandardCharsets.UTF_8)));
        envelope.setTimestamp(System.currentTimeMillis());
        return envelope;
    }

    /**
     * Decodes an inbound request envelope and verifies its plaintext MD5 checksum.
     *
     * @param envelope inbound transport envelope
     * @param originalContentType original request content type carried by the wrapper
     * @return decoded plaintext plus the negotiated SM4 key
     */
    public TransferDecodedPayload decodeRequestEnvelope(final TransferEnvelope envelope,
            final String originalContentType) {
        // 先解出本次请求的 SM4 key，再用该 key 解业务明文。
        final String sm4Key = cryptoService.decryptSm2Key(envelope.getEncryptedKey());
        final String plaintext = cryptoService.decryptBySm4(envelope.getEncryptedData(), sm4Key);
        // 校验完整性
        verifyMd5(plaintext.getBytes(StandardCharsets.UTF_8), envelope.getContentMd5());
        return new TransferDecodedPayload(plaintext, sm4Key, originalContentType);
    }

    /**
     * Creates the encrypted response envelope that mirrors a previously decrypted request.
     *
     * @param plaintext response body bytes
     * @param originalContentType response content type before wrapping
     * @param sm4Key SM4 key negotiated from the matching request
     * @return encoded transport envelope
     */
    public TransferEnvelope createResponseEnvelope(final byte[] plaintext, final String originalContentType,
            final String sm4Key) {
        final TransferEnvelope envelope = new TransferEnvelope();
        envelope.setEncryptedData(cryptoService.encryptBySm4(new String(plaintext, StandardCharsets.UTF_8), sm4Key));
        envelope.setContentMd5(cryptoService.md5Hex(plaintext));
        envelope.setTimestamp(System.currentTimeMillis());
        return envelope;
    }

    /**
     * Decodes an encrypted response envelope and verifies the MD5 checksum.
     *
     * @param envelope response envelope returned by a downstream service
     * @param sm4Key SM4 key negotiated during request encryption
     * @return decrypted response body
     */
    public byte[] decodeResponseEnvelope(final TransferEnvelope envelope, final String sm4Key) {
        final String plaintext = cryptoService.decryptBySm4(envelope.getEncryptedData(), sm4Key);
        final byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
        verifyMd5(bytes, envelope.getContentMd5());
        return bytes;
    }

    /**
     * Verifies the supplied content against the expected hexadecimal MD5 value.
     *
     * @param content raw content bytes
     * @param md5 expected hexadecimal MD5 string; blank values are treated as no-op
     */
    public void verifyMd5(final byte[] content, final String md5) {
        if (StringUtils.hasText(md5) && !md5.equalsIgnoreCase(cryptoService.md5Hex(content))) {
            throw new TransferException("MD5 完整性校验失败");
        }
    }

    /**
     * @param content raw content bytes
     * @return hexadecimal MD5 value
     */
    public String md5Hex(final byte[] content) {
        return cryptoService.md5Hex(content);
    }

    /**
     * @return a newly generated SM4 key suitable for one transport request
     */
    public String randomSm4Key() {
        return cryptoService.randomSm4Key();
    }
}
