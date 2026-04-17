package io.github.jasper.transfer.encrypt.model;

import lombok.Data;

/**
 * 统一的传输层加密信封模型。
 *
 * <p>前端、服务端、OpenFeign 都围绕该结构进行编解码。</p>
 */
@Data
public class TransferEnvelope {

    private String encryptedKey;

    private String encryptedData;

    private String contentMd5;

    private Long timestamp;
}
