package io.github.jasper.transfer.encrypt.web;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解密后请求包装器。
 *
 * <p>通过重写 body / parameterMap / queryString，让 Controller 保持无感知。</p>
 */
public class TransferHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    private final Map<String, String[]> parameterMap;

    private final String queryString;

    private final String contentType;

    public TransferHttpServletRequestWrapper(final HttpServletRequest request, final byte[] body,
            final Map<String, String[]> parameterMap, final String queryString) {
        this(request, body, parameterMap, queryString, null);
    }

    public TransferHttpServletRequestWrapper(final HttpServletRequest request, final byte[] body,
            final Map<String, String[]> parameterMap, final String queryString, final String contentType) {
        super(request);
        this.body = body == null ? new byte[0] : body;
        this.parameterMap = parameterMap == null ? Collections.<String, String[]>emptyMap()
                : new LinkedHashMap<String, String[]>(parameterMap);
        this.queryString = queryString;
        this.contentType = contentType;
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(body);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public String getParameter(final String name) {
        final String[] values = parameterMap.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(parameterMap);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }

    @Override
    public String[] getParameterValues(final String name) {
        return parameterMap.get(name);
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public int getContentLength() {
        return body.length;
    }

    @Override
    public long getContentLengthLong() {
        return body.length;
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType : super.getContentType();
    }
}
