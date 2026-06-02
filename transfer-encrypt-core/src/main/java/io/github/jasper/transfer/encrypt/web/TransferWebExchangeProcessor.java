package io.github.jasper.transfer.encrypt.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.core.TransferConstants;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.core.TransferException;
import io.github.jasper.transfer.encrypt.core.TransferRequestContext;
import io.github.jasper.transfer.encrypt.model.TransferDecodedPayload;
import io.github.jasper.transfer.encrypt.model.TransferEnvelope;
import io.github.jasper.transfer.encrypt.util.TransferJsonUtils;
import io.github.jasper.transfer.encrypt.util.TransferWebUtils;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

/**
 * Servlet-independent HTTP exchange processor shared by Spring 2 and Spring 3 starters.
 */
public class TransferWebExchangeProcessor {

    private static final int HTTP_NO_CONTENT = 204;

    private final ObjectMapper objectMapper;

    private final TransferEnvelopeCodec envelopeCodec;

    /**
     * @param objectMapper Jackson object mapper from the host application
     * @param envelopeCodec shared transport envelope codec
     */
    public TransferWebExchangeProcessor(final ObjectMapper objectMapper, final TransferEnvelopeCodec envelopeCodec) {
        this.objectMapper = objectMapper;
        this.envelopeCodec = envelopeCodec;
    }

    /**
     * Resolves an inbound HTTP request into a wrapper plan without depending on a Servlet namespace.
     *
     * @param input request data read by the concrete starter
     * @return request wrapper plan and transfer context
     */
    public RequestResolution resolveRequest(final RequestInput input) {
        final String contentType = input.getContentType();
        if (TransferWebUtils.isMultipartContentType(contentType)) {
            return RequestResolution.passthrough(new TransferRequestContext(true, false, true, null));
        }

        final byte[] originalBody = input.getOriginalBody();
        final Map<String, String[]> originalParameters = resolveOriginalParameters(input, originalBody);
        final ResolvedEnvelope envelope = resolveEnvelope(input, originalBody);
        if (envelope != null) {
            final TransferDecodedPayload payload = envelopeCodec.decodeRequestEnvelope(envelope.payload,
                    envelope.originalContentType);
            final String plaintext = payload.getPlaintext();
            final byte[] decryptedBody = plaintext.getBytes(StandardCharsets.UTF_8);
            final String innerContentType = payload.getOriginalContentType();
            if (TransferWebUtils.isJsonContentType(innerContentType)) {
                final Map<String, String[]> decryptedParameters = resolveJsonParameters(plaintext);
                mergePlainParameters(originalParameters, decryptedParameters);
                return RequestResolution.wrapped(decryptedBody, decryptedParameters, input.getQueryString(),
                        innerContentType, new TransferRequestContext(true, true, false, payload.getSm4Key()));
            }

            final Map<String, String[]> decryptedParameters =
                    new LinkedHashMap<String, String[]>(TransferWebUtils.parseQueryString(plaintext));
            mergePlainParameters(originalParameters, decryptedParameters);
            final String queryString = "GET".equalsIgnoreCase(input.getMethod()) ? plaintext : input.getQueryString();
            return RequestResolution.wrapped(decryptedBody, decryptedParameters, queryString, innerContentType,
                    new TransferRequestContext(true, true, false, payload.getSm4Key()));
        }

        if (StringUtils.hasText(input.getRequestMd5()) && originalBody.length > 0) {
            envelopeCodec.verifyMd5(originalBody, input.getRequestMd5());
            return RequestResolution.wrapped(originalBody, originalParameters, input.getQueryString(), contentType,
                    new TransferRequestContext(true, false, true, null));
        }
        return RequestResolution.wrapped(originalBody, originalParameters, input.getQueryString(), contentType,
                new TransferRequestContext(true, false, false, null));
    }

    /**
     * Resolves an outbound HTTP response into a header/body rewrite plan.
     *
     * @param input response data captured by the concrete starter
     * @return response write plan
     */
    public ResponseResolution resolveResponse(final ResponseInput input) {
        final byte[] body = input.getBody();
        final String contentType = input.getContentType();
        final String disposition = input.getContentDisposition();
        final TransferRequestContext context = input.getContext();
        final Map<String, String> headers = new LinkedHashMap<String, String>();
        if (context.isEncryptedRequest() && shouldEncryptResponse(input.getStatus(), contentType, disposition, body)) {
            final TransferEnvelope responseEnvelope = envelopeCodec.createResponseEnvelope(body,
                    TransferWebUtils.normalizeContentType(contentType), context.getSm4Key());
            final Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
            wrapper.put(TransferConstants.FIELD_TRANSFER_PAYLOAD,
                    TransferJsonUtils.encodeTransportPayload(objectMapper, responseEnvelope));
            wrapper.put(TransferConstants.FIELD_ORIGINAL_CONTENT_TYPE,
                    TransferWebUtils.normalizeContentType(contentType));
            headers.put(TransferConstants.HEADER_TRANSFER_ENCRYPTED, "true");
            return ResponseResolution.rewrite(TransferJsonUtils.writeBytes(objectMapper, wrapper),
                    MediaType.APPLICATION_JSON_VALUE, StandardCharsets.UTF_8.name(), headers);
        }
        if (body.length > 0 && (context.isFileRequest() || TransferWebUtils.isBinaryResponse(contentType, disposition))) {
            headers.put(TransferConstants.HEADER_CONTENT_MD5, envelopeCodec.md5Hex(body));
        }
        return ResponseResolution.headersOnly(headers);
    }

    private boolean shouldEncryptResponse(final int status, final String contentType, final String disposition,
            final byte[] body) {
        return status != HTTP_NO_CONTENT && body.length > 0 && !TransferWebUtils.isBinaryResponse(contentType, disposition);
    }

    private Map<String, String[]> resolveJsonParameters(final String plaintext) {
        final Object jsonValue = TransferJsonUtils.readValue(objectMapper, plaintext, Object.class);
        if (!(jsonValue instanceof Map)) {
            return new LinkedHashMap<String, String[]>();
        }
        final Map<?, ?> jsonMap = (Map<?, ?>) jsonValue;
        final Map<String, String[]> parameters = new LinkedHashMap<String, String[]>();
        for (final Map.Entry<?, ?> entry : jsonMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            parameters.put(String.valueOf(entry.getKey()), toParameterValues(entry.getValue()));
        }
        return parameters;
    }

    private String[] toParameterValues(final Object value) {
        if (value == null) {
            return new String[] {""};
        }
        if (value instanceof Iterable) {
            final List<String> values = new ArrayList<String>();
            for (final Object item : (Iterable<?>) value) {
                values.add(stringifyParameterValue(item));
            }
            return values.toArray(new String[0]);
        }
        if (value.getClass().isArray()) {
            final int length = Array.getLength(value);
            final String[] values = new String[length];
            for (int index = 0; index < length; index++) {
                values[index] = stringifyParameterValue(Array.get(value, index));
            }
            return values;
        }
        return new String[] {stringifyParameterValue(value)};
    }

    private String stringifyParameterValue(final Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return TransferJsonUtils.writeString(objectMapper, value);
    }

    private Map<String, String[]> resolveOriginalParameters(final RequestInput input, final byte[] originalBody) {
        if (TransferWebUtils.isFormContentType(input.getContentType())) {
            final Map<String, String[]> formParameters =
                    new LinkedHashMap<String, String[]>(TransferWebUtils.parseQueryString(
                            new String(originalBody, StandardCharsets.UTF_8)));
            final Map<String, String[]> requestParameters = input.getParameters();
            for (final Map.Entry<String, String[]> entry : requestParameters.entrySet()) {
                if (!formParameters.containsKey(entry.getKey())) {
                    formParameters.put(entry.getKey(), entry.getValue());
                }
            }
            return formParameters;
        }
        return input.getParameters();
    }

    private void mergePlainParameters(final Map<String, String[]> originalParameters,
            final Map<String, String[]> parameters) {
        for (final Map.Entry<String, String[]> entry : originalParameters.entrySet()) {
            final String name = entry.getKey();
            if (TransferConstants.FIELD_TRANSFER_PAYLOAD.equals(name)) {
                continue;
            }
            if (!parameters.containsKey(name)) {
                parameters.put(name, entry.getValue());
            }
        }
    }

    private ResolvedEnvelope resolveEnvelope(final RequestInput input, final byte[] originalBody) {
        if (originalBody.length > 0 && TransferWebUtils.isJsonContentType(input.getContentType())) {
            final String bodyString = new String(originalBody, StandardCharsets.UTF_8);
            if (TransferWebUtils.isTransferEnvelopeJson(bodyString)) {
                final Map<?, ?> bodyMap = TransferJsonUtils.readValue(objectMapper, bodyString, Map.class);
                final Object compactPayload = bodyMap.get(TransferConstants.FIELD_TRANSFER_PAYLOAD);
                if (!StringUtils.hasText(compactPayload == null ? null : String.valueOf(compactPayload))) {
                    throw new TransferException("transferPayload 缺失");
                }
                final Object originalContentType = bodyMap.get(TransferConstants.FIELD_ORIGINAL_CONTENT_TYPE);
                return new ResolvedEnvelope(
                        TransferJsonUtils.decodeTransportPayload(objectMapper, String.valueOf(compactPayload)),
                        normalizeOriginalContentType(originalContentType == null ? null : String.valueOf(originalContentType),
                                input.getContentType()));
            }
        }
        final Map<String, String[]> parameters = TransferWebUtils.isFormContentType(input.getContentType())
                ? TransferWebUtils.parseQueryString(new String(originalBody, StandardCharsets.UTF_8))
                : input.getParameters();
        final String compactPayload = firstParameter(parameters, TransferConstants.FIELD_TRANSFER_PAYLOAD);
        if (StringUtils.hasText(compactPayload)) {
            return new ResolvedEnvelope(TransferJsonUtils.decodeTransportPayload(objectMapper, compactPayload),
                    normalizeOriginalContentType(firstParameter(parameters, TransferConstants.FIELD_ORIGINAL_CONTENT_TYPE),
                            input.getContentType()));
        }
        return null;
    }

    private String firstParameter(final Map<String, String[]> parameters, final String name) {
        final String[] values = parameters.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    private String normalizeOriginalContentType(final String originalContentType, final String requestContentType) {
        if (StringUtils.hasText(originalContentType)) {
            return originalContentType;
        }
        if (TransferWebUtils.isJsonContentType(requestContentType)) {
            return MediaType.APPLICATION_JSON_VALUE;
        }
        return MediaType.APPLICATION_FORM_URLENCODED_VALUE;
    }

    private static final class ResolvedEnvelope {

        private final TransferEnvelope payload;

        private final String originalContentType;

        private ResolvedEnvelope(final TransferEnvelope payload, final String originalContentType) {
            this.payload = payload;
            this.originalContentType = originalContentType;
        }
    }

    /**
     * Servlet-neutral request data.
     */
    public static final class RequestInput {

        private final String method;

        private final String contentType;

        private final String queryString;

        private final byte[] originalBody;

        private final Map<String, String[]> parameters;

        private final String requestMd5;

        public RequestInput(final String method, final String contentType, final String queryString,
                final byte[] originalBody, final Map<String, String[]> parameters, final String requestMd5) {
            this.method = method;
            this.contentType = contentType;
            this.queryString = queryString;
            this.originalBody = originalBody == null ? new byte[0] : originalBody;
            this.parameters = parameters == null ? Collections.<String, String[]>emptyMap()
                    : new LinkedHashMap<String, String[]>(parameters);
            this.requestMd5 = requestMd5;
        }

        public String getMethod() {
            return method;
        }

        public String getContentType() {
            return contentType;
        }

        public String getQueryString() {
            return queryString;
        }

        public byte[] getOriginalBody() {
            return originalBody;
        }

        public Map<String, String[]> getParameters() {
            return parameters;
        }

        public String getRequestMd5() {
            return requestMd5;
        }
    }

    /**
     * Servlet-neutral request wrapper plan.
     */
    public static final class RequestResolution {

        private final boolean wrapRequest;

        private final byte[] body;

        private final Map<String, String[]> parameters;

        private final String queryString;

        private final String contentType;

        private final TransferRequestContext context;

        private RequestResolution(final boolean wrapRequest, final byte[] body, final Map<String, String[]> parameters,
                final String queryString, final String contentType, final TransferRequestContext context) {
            this.wrapRequest = wrapRequest;
            this.body = body == null ? new byte[0] : body;
            this.parameters = parameters == null ? Collections.<String, String[]>emptyMap()
                    : new LinkedHashMap<String, String[]>(parameters);
            this.queryString = queryString;
            this.contentType = contentType;
            this.context = context;
        }

        private static RequestResolution passthrough(final TransferRequestContext context) {
            return new RequestResolution(false, null, null, null, null, context);
        }

        private static RequestResolution wrapped(final byte[] body, final Map<String, String[]> parameters,
                final String queryString, final String contentType, final TransferRequestContext context) {
            return new RequestResolution(true, body, parameters, queryString, contentType, context);
        }

        public boolean isWrapRequest() {
            return wrapRequest;
        }

        public byte[] getBody() {
            return body;
        }

        public Map<String, String[]> getParameters() {
            return parameters;
        }

        public String getQueryString() {
            return queryString;
        }

        public String getContentType() {
            return contentType;
        }

        public TransferRequestContext getContext() {
            return context;
        }
    }

    /**
     * Servlet-neutral response data.
     */
    public static final class ResponseInput {

        private final int status;

        private final byte[] body;

        private final String contentType;

        private final String contentDisposition;

        private final TransferRequestContext context;

        public ResponseInput(final int status, final byte[] body, final String contentType,
                final String contentDisposition, final TransferRequestContext context) {
            this.status = status;
            this.body = body == null ? new byte[0] : body;
            this.contentType = contentType;
            this.contentDisposition = contentDisposition;
            this.context = context;
        }

        public int getStatus() {
            return status;
        }

        public byte[] getBody() {
            return body;
        }

        public String getContentType() {
            return contentType;
        }

        public String getContentDisposition() {
            return contentDisposition;
        }

        public TransferRequestContext getContext() {
            return context;
        }
    }

    /**
     * Servlet-neutral response write plan.
     */
    public static final class ResponseResolution {

        private final boolean rewriteBody;

        private final byte[] body;

        private final String contentType;

        private final String characterEncoding;

        private final Map<String, String> headers;

        private ResponseResolution(final boolean rewriteBody, final byte[] body, final String contentType,
                final String characterEncoding, final Map<String, String> headers) {
            this.rewriteBody = rewriteBody;
            this.body = body == null ? new byte[0] : body;
            this.contentType = contentType;
            this.characterEncoding = characterEncoding;
            this.headers = headers == null ? Collections.<String, String>emptyMap()
                    : new LinkedHashMap<String, String>(headers);
        }

        private static ResponseResolution rewrite(final byte[] body, final String contentType,
                final String characterEncoding, final Map<String, String> headers) {
            return new ResponseResolution(true, body, contentType, characterEncoding, headers);
        }

        private static ResponseResolution headersOnly(final Map<String, String> headers) {
            return new ResponseResolution(false, null, null, null, headers);
        }

        public boolean isRewriteBody() {
            return rewriteBody;
        }

        public byte[] getBody() {
            return body;
        }

        public String getContentType() {
            return contentType;
        }

        public String getCharacterEncoding() {
            return characterEncoding;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }
    }
}
