package io.github.jasper.transfer.encrypt.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Client;
import feign.Request;
import feign.Response;
import feign.Util;
import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferConstants;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.model.TransferEnvelope;
import io.github.jasper.transfer.encrypt.util.TransferJsonUtils;
import io.github.jasper.transfer.encrypt.util.TransferWebUtils;
import io.github.jasper.transfer.encrypt.web.TransferPathMatcher;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

/**
 * OpenFeign 底层 Client 包装器。
 *
 * <p>只有在调用上下文显式开启时才会工作，从而保证：</p>
 * <ul>
 *     <li>标注注解的 Feign 方法走加密协议</li>
 *     <li>未标注的普通 Feign 调用保持原样</li>
 * </ul>
 */
public class TransferFeignClientWrapper implements Client {

    private final Client delegate;

    private final TransferEnvelopeCodec envelopeCodec;

    private final ObjectMapper objectMapper;

    private final TransferPathMatcher pathMatcher;

    private final TransferEncryptProperties properties;

    public TransferFeignClientWrapper(final Client delegate, final TransferEnvelopeCodec envelopeCodec,
            final ObjectMapper objectMapper, final TransferPathMatcher pathMatcher,
            final TransferEncryptProperties properties) {
        this.delegate = delegate;
        this.envelopeCodec = envelopeCodec;
        this.objectMapper = objectMapper;
        this.pathMatcher = pathMatcher;
        this.properties = properties;
    }

    @Override
    public Response execute(final Request request, final Request.Options options) throws IOException {
        if (!TransferFeignInvocationContext.isEnabled()) {
            return delegate.execute(request, options);
        }
        final RequestExecution execution = encryptRequestIfNecessary(request);
        final Response response = delegate.execute(execution.request, options);
        return decryptResponseIfNecessary(response, execution.sm4Key);
    }

    private RequestExecution encryptRequestIfNecessary(final Request request) {
        final URI uri = URI.create(request.url());
        if (!pathMatcher.matches(uri.getPath())) {
            return new RequestExecution(request, null);
        }
        final boolean md5Enabled = TransferFeignInvocationContext.isMd5Enabled();
        final Charset charset = request.charset() == null ? StandardCharsets.UTF_8 : request.charset();
        final byte[] bodyBytes = request.body() == null ? new byte[0] : request.body();
        final String contentType = firstHeader(request.headers(), "Content-Type");
        if (TransferWebUtils.isMultipartContentType(contentType)
                || TransferWebUtils.isBinaryResponse(contentType, firstHeader(request.headers(), "Content-Disposition"))) {
            return new RequestExecution(md5Enabled
                    ? withHeader(request, TransferConstants.HEADER_CONTENT_MD5,
                            bodyBytes.length == 0 ? null : envelopeCodec.md5Hex(bodyBytes))
                    : request, null);
        }

        final String rawQuery = uri.getRawQuery();
        final String publicKey = resolvePublicKey(uri);
        if (StringUtils.hasText(rawQuery) && bodyBytes.length == 0) {
            final String sm4Key = envelopeCodec.randomSm4Key();
            final TransferEnvelope envelope = envelopeCodec.createRequestEnvelope(rawQuery,
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE, sm4Key, publicKey);
            final String encryptedQuery = buildCompactEnvelopeQuery(envelope,
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE);
            return new RequestExecution(rebuildRequest(request, replaceQuery(request.url(), encryptedQuery),
                    null, charset, removeContentLength(request.headers())), sm4Key);
        }

        if (bodyBytes.length == 0) {
            return new RequestExecution(request, null);
        }

        final String plaintext = new String(bodyBytes, charset);
        if (TransferWebUtils.isFormContentType(contentType)) {
            final String sm4Key = envelopeCodec.randomSm4Key();
            final TransferEnvelope envelope = envelopeCodec.createRequestEnvelope(plaintext, contentType, sm4Key,
                    publicKey);
            final byte[] encryptedBody = buildCompactEnvelopeQuery(envelope, contentType).getBytes(StandardCharsets.UTF_8);
            return new RequestExecution(rebuildRequest(request, request.url(), encryptedBody, StandardCharsets.UTF_8,
                    removeContentLength(request.headers())), sm4Key);
        }
        if (TransferWebUtils.isJsonContentType(contentType)) {
            final String sm4Key = envelopeCodec.randomSm4Key();
            final TransferEnvelope envelope = envelopeCodec.createRequestEnvelope(plaintext, contentType, sm4Key,
                    publicKey);
            final Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
            wrapper.put(TransferConstants.FIELD_TRANSFER_PAYLOAD,
                    TransferJsonUtils.encodeTransportPayload(objectMapper, envelope));
            wrapper.put(TransferConstants.FIELD_ORIGINAL_CONTENT_TYPE, contentType);
            final byte[] encryptedBody = TransferJsonUtils.writeBytes(objectMapper, wrapper);
            return new RequestExecution(rebuildRequest(request, request.url(), encryptedBody, StandardCharsets.UTF_8,
                    removeContentLength(request.headers())), sm4Key);
        }
        return new RequestExecution(md5Enabled
                ? withHeader(request, TransferConstants.HEADER_CONTENT_MD5, envelopeCodec.md5Hex(bodyBytes))
                : request, null);
    }

    private Response decryptResponseIfNecessary(final Response response, final String sm4Key) throws IOException {
        if (response.body() == null) {
            return response;
        }
        final boolean md5Enabled = TransferFeignInvocationContext.isMd5Enabled();
        final byte[] responseBody = Util.toByteArray(response.body().asInputStream());
        if (responseBody.length == 0) {
            return rebuildResponse(response, responseBody, response.headers());
        }
        final String payload = new String(responseBody, StandardCharsets.UTF_8);
        final boolean encryptedEnvelope = sm4Key != null && TransferWebUtils.isTransferEnvelopeJson(payload);
        if (encryptedEnvelope) {
            final Map<?, ?> bodyMap = TransferJsonUtils.readValue(objectMapper, payload, Map.class);
            final Object compactPayload = bodyMap.get(TransferConstants.FIELD_TRANSFER_PAYLOAD);
            if (!StringUtils.hasText(compactPayload == null ? null : String.valueOf(compactPayload))) {
                return rebuildResponse(response, responseBody, response.headers());
            }
            final TransferEnvelope envelope =
                    TransferJsonUtils.decodeTransportPayload(objectMapper, String.valueOf(compactPayload));
            final byte[] decryptedBody = envelopeCodec.decodeResponseEnvelope(envelope, sm4Key);
            final Object originalContentType = bodyMap.get(TransferConstants.FIELD_ORIGINAL_CONTENT_TYPE);
            final Map<String, Collection<String>> headers = new LinkedHashMap<>(response.headers());
            headers.put("Content-Type", singletonCollection(
                    StringUtils.hasText(originalContentType == null ? null : String.valueOf(originalContentType))
                            ? String.valueOf(originalContentType)
                            : MediaType.APPLICATION_JSON_VALUE));
            headers.remove(TransferConstants.HEADER_TRANSFER_ENCRYPTED);
            headers.remove("Content-Length");
            headers.remove(TransferConstants.HEADER_CONTENT_MD5);
            return rebuildResponse(response, decryptedBody, headers);
        }
        final String contentMd5 = firstHeader(response.headers(), TransferConstants.HEADER_CONTENT_MD5);
        if (md5Enabled && StringUtils.hasText(contentMd5)) {
            envelopeCodec.verifyMd5(responseBody, contentMd5);
        }
        if (sm4Key == null || !TransferWebUtils.isTransferEnvelopeJson(payload)) {
            return rebuildResponse(response, responseBody, response.headers());
        }
        return rebuildResponse(response, responseBody, response.headers());
    }

    private Response rebuildResponse(final Response response, final byte[] body,
            final Map<String, Collection<String>> headers) {
        return Response.builder().request(response.request()).status(response.status()).reason(response.reason())
                .headers(headers).body(body).build();
    }

    private Request withHeader(final Request request, final String headerName, final String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return request;
        }
        final Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>(request.headers());
        headers.put(headerName, singletonCollection(headerValue));
        return rebuildRequest(request, request.url(), request.body(), request.charset(), headers);
    }

    private Map<String, Collection<String>> removeContentLength(final Map<String, Collection<String>> source) {
        final Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>(source);
        headers.remove("Content-Length");
        return headers;
    }

    private String buildCompactEnvelopeQuery(final TransferEnvelope envelope, final String originalContentType) {
        final Map<String, List<String>> query = new LinkedHashMap<>();
        query.put(TransferConstants.FIELD_TRANSFER_PAYLOAD,
                singletonList(TransferJsonUtils.encodeTransportPayload(objectMapper, envelope)));
        query.put(TransferConstants.FIELD_ORIGINAL_CONTENT_TYPE,
                singletonList(StringUtils.hasText(originalContentType) ? originalContentType
                        : MediaType.APPLICATION_FORM_URLENCODED_VALUE));
        return TransferWebUtils.toQueryString(query);
    }

    private Request rebuildRequest(final Request request, final String url, final byte[] body, final Charset charset,
            final Map<String, Collection<String>> headers) {
        return Request.create(request.httpMethod(), url, headers, body, charset, request.requestTemplate());
    }

    private String replaceQuery(final String url, final String query) {
        final int index = url.indexOf('?');
        final String prefix = index >= 0 ? url.substring(0, index) : url;
        return prefix + '?' + query;
    }

    private String resolvePublicKey(final URI uri) {
        final String publicKeyAlias = TransferFeignInvocationContext.getPublicKeyAlias();
        if (StringUtils.hasText(publicKeyAlias) && properties.getFeignPublicKeys().containsKey(publicKeyAlias)) {
            // 优先使用注解显式声明的公钥别名。
            return properties.getFeignPublicKeys().get(publicKeyAlias);
        }
        final String host = uri.getHost();
        if (StringUtils.hasText(host) && properties.getFeignPublicKeys().containsKey(host)) {
            return properties.getFeignPublicKeys().get(host);
        }
        final String authority = uri.getAuthority();
        if (StringUtils.hasText(authority) && properties.getFeignPublicKeys().containsKey(authority)) {
            return properties.getFeignPublicKeys().get(authority);
        }
        return properties.getPublicKey();
    }

    private String firstHeader(final Map<String, Collection<String>> headers, final String key) {
        for (final Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key) && !entry.getValue().isEmpty()) {
                return entry.getValue().iterator().next();
            }
        }
        return null;
    }

    private List<String> singletonList(final String value) {
        final List<String> values = new ArrayList<String>(1);
        values.add(value);
        return values;
    }

    private Collection<String> singletonCollection(final String value) {
        return singletonList(value);
    }

    private static final class RequestExecution {

        private final Request request;

        private final String sm4Key;

        private RequestExecution(final Request request, final String sm4Key) {
            this.request = request;
            this.sm4Key = sm4Key;
        }
    }
}
