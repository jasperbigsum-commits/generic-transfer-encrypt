package io.github.jasper.transfer.encrypt.model;

/**
 * 请求信封解密后的中间结果。
 */
public class TransferDecodedPayload {

    private final String plaintext;

    private final String sm4Key;

    private final String originalContentType;

    /**
     * @param plaintext decrypted business payload
     * @param sm4Key negotiated SM4 key used for the current exchange
     * @param originalContentType original request content type before wrapping
     */
    public TransferDecodedPayload(final String plaintext, final String sm4Key, final String originalContentType) {
        this.plaintext = plaintext;
        this.sm4Key = sm4Key;
        this.originalContentType = originalContentType;
    }

    /**
     * @return decrypted business payload
     */
    public String getPlaintext() {
        return plaintext;
    }

    /**
     * @return negotiated SM4 key used for the current exchange
     */
    public String getSm4Key() {
        return sm4Key;
    }

    /**
     * @return original request content type before transport wrapping
     */
    public String getOriginalContentType() {
        return originalContentType;
    }
}
