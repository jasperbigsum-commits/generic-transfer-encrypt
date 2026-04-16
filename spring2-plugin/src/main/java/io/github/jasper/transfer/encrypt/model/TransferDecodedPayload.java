package io.github.jasper.transfer.encrypt.model;

/**
 * 请求信封解密后的中间结果。
 */
public class TransferDecodedPayload {

    private final String plaintext;

    private final String sm4Key;

    private final String originalContentType;

    public TransferDecodedPayload(final String plaintext, final String sm4Key, final String originalContentType) {
        this.plaintext = plaintext;
        this.sm4Key = sm4Key;
        this.originalContentType = originalContentType;
    }

    public String getPlaintext() {
        return plaintext;
    }

    public String getSm4Key() {
        return sm4Key;
    }

    public String getOriginalContentType() {
        return originalContentType;
    }
}
