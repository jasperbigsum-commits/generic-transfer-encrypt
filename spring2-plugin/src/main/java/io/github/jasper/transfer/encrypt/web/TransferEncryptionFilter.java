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
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spring MVC 传输层过滤器。
 *
 * <p>职责：</p>
 * <ul>
 *     <li>识别是否命中加密路径</li>
 *     <li>解密 JSON / form / query 请求</li>
 *     <li>保持 Controller 原有参数绑定能力</li>
 *     <li>在需要时加密响应或附加文件 MD5</li>
 * </ul>
 */
public class TransferEncryptionFilter extends OncePerRequestFilter {

    private static final Logger log = Logger.getLogger(TransferEncryptionFilter.class.getName());

    private final ObjectMapper objectMapper;

    private final TransferEnvelopeCodec envelopeCodec;

    private final TransferPathMatcher pathMatcher;

    public TransferEncryptionFilter(final ObjectMapper objectMapper, final TransferEnvelopeCodec envelopeCodec,
            final TransferPathMatcher pathMatcher) {
        this.objectMapper = objectMapper;
        this.envelopeCodec = envelopeCodec;
        this.pathMatcher = pathMatcher;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        if (!pathMatcher.matches(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final RequestResolution resolution = resolveRequest(request);
            final HttpServletRequest requestToUse =
                    resolution.requestWrapper == null ? request : resolution.requestWrapper;
            requestToUse.setAttribute(TransferConstants.REQUEST_ATTRIBUTE, resolution.context);
            final ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
            filterChain.doFilter(requestToUse, responseWrapper);
            writeResponse(requestToUse, responseWrapper, resolution.context);
        } catch (final TransferException ex) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } catch (final Exception ex) {
            log.log(Level.SEVERE,
                    "Transfer encryption failed on [" + request.getMethod() + " " + request.getRequestURI() + "]", ex);
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "传输层加解密处理异常");
        }
    }

    private RequestResolution resolveRequest(final HttpServletRequest request) throws IOException {
        final String contentType = request.getContentType();
        final boolean multipart = TransferWebUtils.isMultipartContentType(contentType);
        // 若是或文件混合表单的直接非加密
        if (multipart) {
            return new RequestResolution(null, new TransferRequestContext(true, false, true, null));
        }
        final byte[] originalBody = TransferWebUtils.readBody(request);
        final Map<String, String[]> originalParameters = resolveOriginalParameters(request, originalBody);

        final TransferEnvelope envelope = resolveEnvelope(request, originalBody);
        if (envelope != null) {
            final TransferDecodedPayload payload = envelopeCodec.decodeRequestEnvelope(envelope);
            final String plaintext = payload.getPlaintext();
            final byte[] decryptedBody = plaintext.getBytes(StandardCharsets.UTF_8);
            if (TransferWebUtils.isJsonContentType(contentType)) {
                return new RequestResolution(
                        new TransferHttpServletRequestWrapper(request, decryptedBody,
                                originalParameters, request.getQueryString()),
                        new TransferRequestContext(true, true, false, payload.getSm4Key()));
            }

            final Map<String, String[]> decryptedParameters = new LinkedHashMap<String, String[]>(
                    TransferWebUtils.parseQueryString(plaintext));
            mergePlainParameters(originalParameters, decryptedParameters);
            final String queryString = "GET".equalsIgnoreCase(request.getMethod()) ? plaintext : request.getQueryString();
            return new RequestResolution(new TransferHttpServletRequestWrapper(request, decryptedBody, decryptedParameters,
                    queryString), new TransferRequestContext(true, true, false, payload.getSm4Key()));
        }

        final String requestMd5 = request.getHeader(TransferConstants.HEADER_CONTENT_MD5);
        if (StringUtils.hasText(requestMd5) && originalBody.length > 0) {
            envelopeCodec.verifyMd5(originalBody, requestMd5);
            return new RequestResolution(new TransferHttpServletRequestWrapper(request, originalBody,
                    originalParameters, request.getQueryString()),
                    new TransferRequestContext(true, false, true, null));
        }
        return new RequestResolution(new TransferHttpServletRequestWrapper(request, originalBody, originalParameters,
                request.getQueryString()),
                new TransferRequestContext(true, false, false, null));
    }

    private Map<String, String[]> resolveOriginalParameters(final HttpServletRequest request, final byte[] originalBody) {
        if (TransferWebUtils.isFormContentType(request.getContentType())) {
            final Map<String, String[]> formParameters =
                    new LinkedHashMap<String, String[]>(TransferWebUtils.parseQueryString(
                            new String(originalBody, StandardCharsets.UTF_8)));
            final Map<String, String[]> queryParameters = TransferWebUtils.extractParameters(request);
            for (final Map.Entry<String, String[]> entry : queryParameters.entrySet()) {
                if (!formParameters.containsKey(entry.getKey())) {
                    formParameters.put(entry.getKey(), entry.getValue());
                }
            }
            return formParameters;
        }
        return TransferWebUtils.extractParameters(request);
    }

    private void mergePlainParameters(final Map<String, String[]> originalParameters,
            final Map<String, String[]> parameters) {
        for (final Map.Entry<String, String[]> entry : originalParameters.entrySet()) {
            final String name = entry.getKey();
            if (TransferConstants.FIELD_ENCRYPTED_KEY.equals(name) || TransferConstants.FIELD_ENCRYPTED_DATA.equals(name)
                    || TransferConstants.FIELD_CONTENT_MD5.equals(name)) {
                continue;
            }
            if (!parameters.containsKey(name)) {
                parameters.put(name, entry.getValue());
            }
        }
    }

    private TransferEnvelope resolveEnvelope(final HttpServletRequest request, final byte[] originalBody) {
        if (originalBody.length > 0 && TransferWebUtils.isJsonContentType(request.getContentType())) {
            final String bodyString = new String(originalBody, StandardCharsets.UTF_8);
            if (TransferWebUtils.isTransferEnvelopeJson(bodyString)) {
                return TransferJsonUtils.readValue(objectMapper, bodyString, TransferEnvelope.class);
            }
        }
        final Map<String, String[]> parameters = TransferWebUtils.isFormContentType(request.getContentType())
                ? TransferWebUtils.parseQueryString(new String(originalBody, StandardCharsets.UTF_8))
                : TransferWebUtils.extractParameters(request);
        final String encryptedData = firstParameter(parameters, TransferConstants.FIELD_ENCRYPTED_DATA);
        final String encryptedKey = firstParameter(parameters, TransferConstants.FIELD_ENCRYPTED_KEY);
        if (StringUtils.hasText(encryptedData) && StringUtils.hasText(encryptedKey)) {
            final TransferEnvelope envelope = new TransferEnvelope();
            envelope.setAlgorithm(TransferConstants.ALGORITHM);
            envelope.setEncryptedData(encryptedData);
            envelope.setEncryptedKey(encryptedKey);
            envelope.setContentMd5(firstParameter(parameters, TransferConstants.FIELD_CONTENT_MD5));
            envelope.setOriginalContentType(request.getContentType());
            return envelope;
        }
        return null;
    }

    private void writeResponse(final HttpServletRequest request, final ContentCachingResponseWrapper responseWrapper,
            final TransferRequestContext context) throws IOException {
        final byte[] body = responseWrapper.getContentAsByteArray();
        final String contentType = responseWrapper.getContentType();
        final String disposition = responseWrapper.getHeader("Content-Disposition");
        if (context.isEncryptedRequest() && shouldEncryptResponse(responseWrapper, contentType, disposition, body)) {
            final TransferEnvelope responseEnvelope = envelopeCodec.createResponseEnvelope(body,
                    TransferWebUtils.normalizeContentType(contentType), context.getSm4Key());
            final byte[] encryptedBody = TransferJsonUtils.writeBytes(objectMapper, responseEnvelope);
            responseWrapper.resetBuffer();
            responseWrapper.setHeader(TransferConstants.HEADER_TRANSFER_ENCRYPTED, "true");
            responseWrapper.setContentType(MediaType.APPLICATION_JSON_VALUE);
            responseWrapper.setCharacterEncoding(StandardCharsets.UTF_8.name());
            responseWrapper.setContentLength(encryptedBody.length);
            responseWrapper.getOutputStream().write(encryptedBody);
        } else if (body.length > 0
                && (context.isFileRequest() || TransferWebUtils.isBinaryResponse(contentType, disposition))) {
            responseWrapper.setHeader(TransferConstants.HEADER_CONTENT_MD5, envelopeCodec.md5Hex(body));
        }
        responseWrapper.copyBodyToResponse();
    }

    private boolean shouldEncryptResponse(final ContentCachingResponseWrapper responseWrapper, final String contentType,
            final String disposition, final byte[] body) {
        return responseWrapper.getStatus() != HttpServletResponse.SC_NO_CONTENT && body.length > 0
                && !TransferWebUtils.isBinaryResponse(contentType, disposition);
    }

    private void writeError(final HttpServletResponse response, final int status, final String message)
            throws IOException {
        response.resetBuffer();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(TransferJsonUtils.writeString(objectMapper,
                new ErrorBody("TRANSFER_ENCRYPT_ERROR", message)));
        response.flushBuffer();
    }

    private String firstParameter(final Map<String, String[]> parameters, final String name) {
        final String[] values = parameters.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    private static final class RequestResolution {

        private final TransferHttpServletRequestWrapper requestWrapper;

        private final TransferRequestContext context;

        private RequestResolution(final TransferHttpServletRequestWrapper requestWrapper,
                final TransferRequestContext context) {
            this.requestWrapper = requestWrapper;
            this.context = context;
        }
    }

    private static final class ErrorBody {

        private final String code;

        private final String message;

        private ErrorBody(final String code, final String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
