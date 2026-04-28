package io.github.jasper.transfer.encrypt.model;

/**
 * 统一的传输层加密信封模型。
 *
 * <p>前端、服务端、OpenFeign 都围绕该结构进行编解码。</p>
 */
public class TransferEnvelope {

    private String encryptedKey;

    private String encryptedData;

    private String contentMd5;

    private Long timestamp;

    /**
     * @return SM2-encrypted SM4 session key
     */
    public String getEncryptedKey() {
        return encryptedKey;
    }

    /**
     * @param encryptedKey SM2-encrypted SM4 session key
     */
    public void setEncryptedKey(final String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }

    /**
     * @return SM4-encrypted business payload
     */
    public String getEncryptedData() {
        return encryptedData;
    }

    /**
     * @param encryptedData SM4-encrypted business payload
     */
    public void setEncryptedData(final String encryptedData) {
        this.encryptedData = encryptedData;
    }

    /**
     * @return hexadecimal MD5 of the plaintext payload
     */
    public String getContentMd5() {
        return contentMd5;
    }

    /**
     * @param contentMd5 hexadecimal MD5 of the plaintext payload
     */
    public void setContentMd5(final String contentMd5) {
        this.contentMd5 = contentMd5;
    }

    /**
     * @return sender-side epoch-millis timestamp
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp sender-side epoch-millis timestamp
     */
    public void setTimestamp(final Long timestamp) {
        this.timestamp = timestamp;
    }
}
