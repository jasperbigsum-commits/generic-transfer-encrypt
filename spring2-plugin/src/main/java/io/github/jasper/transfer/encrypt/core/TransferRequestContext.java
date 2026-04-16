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

    public TransferRequestContext(final boolean matched, final boolean encryptedRequest, final boolean fileRequest,
            final String sm4Key) {
        this.matched = matched;
        this.encryptedRequest = encryptedRequest;
        this.fileRequest = fileRequest;
        this.sm4Key = sm4Key;
    }

    public boolean isMatched() {
        return matched;
    }

    public boolean isEncryptedRequest() {
        return encryptedRequest;
    }

    public boolean isFileRequest() {
        return fileRequest;
    }

    public String getSm4Key() {
        return sm4Key;
    }
}
