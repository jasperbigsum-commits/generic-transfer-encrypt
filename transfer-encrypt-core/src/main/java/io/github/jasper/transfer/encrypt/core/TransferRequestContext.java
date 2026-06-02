package io.github.jasper.transfer.encrypt.core;

/**
 * 保存当前 HTTP 请求在传输层处理后的上下文信息。
 *
 * <p>该对象会挂到 request attribute 中，供后续 MVC 与业务代码感知：</p>
 * <ul>
 *     <li>当前请求是否命中加密路径</li>
 *     <li>本次请求是否已经完成解密</li>
 *     <li>是否属于文件/二进制请求</li>
 *     <li>本次协商得到的 SM4 key</li>
 * </ul>
 */
public class TransferRequestContext {

    private final boolean matched;

    private final boolean encryptedRequest;

    private final boolean fileRequest;

    private final String sm4Key;

    /**
     * @param matched whether the request path matched the include/exclude rules
     * @param encryptedRequest whether the request payload was received as an encrypted envelope
     * @param fileRequest whether the request is treated as multipart or binary passthrough
     * @param sm4Key per-request SM4 key used for matching response encryption, or {@code null} when not applicable
     */
    public TransferRequestContext(final boolean matched, final boolean encryptedRequest, final boolean fileRequest,
            final String sm4Key) {
        this.matched = matched;
        this.encryptedRequest = encryptedRequest;
        this.fileRequest = fileRequest;
        this.sm4Key = sm4Key;
    }

    /**
     * @return whether the current request URI was selected for transport-layer processing
     */
    public boolean isMatched() {
        return matched;
    }

    /**
     * @return whether the inbound request payload was successfully decrypted from a transport envelope
     */
    public boolean isEncryptedRequest() {
        return encryptedRequest;
    }

    /**
     * @return whether the request should stay in file or binary passthrough mode
     */
    public boolean isFileRequest() {
        return fileRequest;
    }

    /**
     * @return the negotiated SM4 key for the current request, or {@code null} when response encryption is not needed
     */
    public String getSm4Key() {
        return sm4Key;
    }
}
