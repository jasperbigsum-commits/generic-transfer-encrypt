package io.github.jasper.transfer.encrypt.model;

/**
 * 统一的传输层加密信封模型。
 *
 * <p>前端、服务端、OpenFeign 都围绕该结构进行编解码。</p>
 */
public class TransferEnvelope {

    private String algorithm;

    private String encryptedKey;

    private String encryptedData;

    private String contentMd5;

    private String originalContentType;

    private Long timestamp;

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(final String algorithm) {
        this.algorithm = algorithm;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public void setEncryptedKey(final String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }

    public String getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(final String encryptedData) {
        this.encryptedData = encryptedData;
    }

    public String getContentMd5() {
        return contentMd5;
    }

    public void setContentMd5(final String contentMd5) {
        this.contentMd5 = contentMd5;
    }

    public String getOriginalContentType() {
        return originalContentType;
    }

    public void setOriginalContentType(final String originalContentType) {
        this.originalContentType = originalContentType;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final Long timestamp) {
        this.timestamp = timestamp;
    }
}
