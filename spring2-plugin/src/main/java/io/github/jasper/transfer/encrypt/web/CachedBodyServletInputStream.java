package io.github.jasper.transfer.encrypt.web;

import java.io.ByteArrayInputStream;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

/**
 * 基于字节数组重放请求体的输入流实现。
 *
 * <p>用于把已经解密或缓存过的请求体重新交给 Spring MVC 消费。</p>
 */
public class CachedBodyServletInputStream extends ServletInputStream {

    private final ByteArrayInputStream inputStream;

    public CachedBodyServletInputStream(final byte[] body) {
        this.inputStream = new ByteArrayInputStream(body);
    }

    @Override
    public int read() {
        return inputStream.read();
    }

    @Override
    public boolean isFinished() {
        return inputStream.available() == 0;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(final ReadListener readListener) {
        throw new UnsupportedOperationException("同步请求不支持 ReadListener");
    }
}
