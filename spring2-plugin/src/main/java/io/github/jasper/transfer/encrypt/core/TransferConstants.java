package io.github.jasper.transfer.encrypt.core;

/**
 * 统一维护协议字段、请求头和请求上下文常量，避免前后实现出现魔法字符串。
 */
public final class TransferConstants {

    public static final String ALGORITHM = "SM2_SM4";

    public static final String FIELD_ENCRYPTED_KEY = "encryptedKey";

    public static final String FIELD_ENCRYPTED_DATA = "encryptedData";

    public static final String FIELD_CONTENT_MD5 = "contentMd5";

    public static final String FIELD_ORIGINAL_CONTENT_TYPE = "originalContentType";

    public static final String HEADER_TRANSFER_ENCRYPTED = "X-Transfer-Encrypted";

    public static final String HEADER_CONTENT_MD5 = "X-Transfer-Content-MD5";

    public static final String REQUEST_ATTRIBUTE = TransferRequestContext.class.getName();

    private TransferConstants() {
    }
}
