package io.github.jasper.transfer.encrypt.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.core.TransferConstants;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.core.TransferException;
import io.github.jasper.transfer.encrypt.core.TransferRequestContext;
import io.github.jasper.transfer.encrypt.util.TransferJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

    private static final Logger log = LoggerFactory.getLogger(TransferEncryptionFilter.class.getName());

    private final ObjectMapper objectMapper;

    private final TransferPathMatcher pathMatcher;

    private final TransferWebExchangeProcessor exchangeProcessor;

    public TransferEncryptionFilter(final ObjectMapper objectMapper, final TransferEnvelopeCodec envelopeCodec,
            final TransferPathMatcher pathMatcher) {
        this.objectMapper = objectMapper;
        this.pathMatcher = pathMatcher;
        this.exchangeProcessor = new TransferWebExchangeProcessor(objectMapper, envelopeCodec);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        if (!pathMatcher.matches(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final TransferWebExchangeProcessor.RequestResolution resolution = resolveRequest(request);
            final HttpServletRequest requestToUse = resolution.isWrapRequest()
                    ? new TransferHttpServletRequestWrapper(request, resolution.getBody(), resolution.getParameters(),
                            resolution.getQueryString(), resolution.getContentType())
                    : request;
            requestToUse.setAttribute(TransferConstants.REQUEST_ATTRIBUTE, resolution.getContext());
            final ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
            filterChain.doFilter(requestToUse, responseWrapper);
            writeResponse(responseWrapper, resolution.getContext());
        } catch (final TransferException ex) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } catch (final Exception ex) {
            if (response.isCommitted() || isClientAbort(ex)) {
                log.warn("Skip transfer error response on [{} {}] because the client connection is already closed",
                        request.getMethod(), request.getRequestURI(), ex);
                return;
            }
            log.error("Transfer encryption failed on [{} {}]", request.getMethod(), request.getRequestURI(), ex);
            // 实际不是我这边的错误应该由第三方进行处理，而不是吃掉异常
            throw ex;
        }
    }

    private TransferWebExchangeProcessor.RequestResolution resolveRequest(final HttpServletRequest request)
            throws IOException {
        final TransferWebExchangeProcessor.RequestBodyReadPlan readPlan =
                exchangeProcessor.planRequestBodyRead(request.getContentType());
        if (!readPlan.shouldReadBody()) {
            return readPlan.getPassthroughResolution();
        }
        final byte[] originalBody = TransferServletWebUtils.readBody(request);
        return exchangeProcessor.resolveRequest(new TransferWebExchangeProcessor.RequestInput(request.getMethod(),
                request.getContentType(), request.getQueryString(), originalBody,
                TransferServletWebUtils.extractParameters(request), request.getHeader(TransferConstants.HEADER_CONTENT_MD5)));
    }

    private void writeResponse(final ContentCachingResponseWrapper responseWrapper, final TransferRequestContext context)
            throws IOException {
        final byte[] body = responseWrapper.getContentAsByteArray();
        final String contentType = responseWrapper.getContentType();
        final String disposition = responseWrapper.getHeader("Content-Disposition");
        final TransferWebExchangeProcessor.ResponseResolution resolution = exchangeProcessor.resolveResponse(
                new TransferWebExchangeProcessor.ResponseInput(responseWrapper.getStatus(), body, contentType,
                        disposition, context));
        for (final Map.Entry<String, String> entry : resolution.getHeaders().entrySet()) {
            responseWrapper.setHeader(entry.getKey(), entry.getValue());
        }
        if (resolution.isRewriteBody()) {
            try {
                final byte[] encryptedBody = resolution.getBody();
                responseWrapper.resetBuffer();
                for (final Map.Entry<String, String> entry : resolution.getHeaders().entrySet()) {
                    responseWrapper.setHeader(entry.getKey(), entry.getValue());
                }
                responseWrapper.setContentType(resolution.getContentType());
                responseWrapper.setCharacterEncoding(resolution.getCharacterEncoding());
                responseWrapper.setContentLength(encryptedBody.length);
                responseWrapper.getOutputStream().write(encryptedBody);
            } catch (final IOException e) {
                throw new TransferException("加密重写响应体异常", e);
            }
        }
        responseWrapper.copyBodyToResponse();
    }

    private void writeError(final HttpServletResponse response, final int status, final String message)
            throws IOException {
        if (response.isCommitted()) {
            log.warn("Skip transfer error response because the servlet response has already been committed");
            return;
        }
        response.resetBuffer();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(TransferJsonUtils.writeString(objectMapper,
                new ErrorBody("TRANSFER_ENCRYPT_ERROR", message)));
        response.flushBuffer();
    }

    private boolean isClientAbort(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            final String simpleName = current.getClass().getSimpleName();
            if ("ClientAbortException".equals(simpleName) || "EofException".equals(simpleName)
                    || "EOFException".equals(simpleName)) {
                return true;
            }
            final String message = current.getMessage();
            if (message != null) {
                final String normalized = message.toLowerCase();
                if (normalized.contains("broken pipe") || normalized.contains("connection reset")
                        || normalized.contains("forcibly closed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
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
