package io.github.jasper.transfer.encrypt.core;

/**
 * 传输层加解密相关的统一运行时异常。
 */
public class TransferException extends RuntimeException {

    public TransferException(final String message) {
        super(message);
    }

    public TransferException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
