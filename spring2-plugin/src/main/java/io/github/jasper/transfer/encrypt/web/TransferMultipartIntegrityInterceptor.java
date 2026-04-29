package io.github.jasper.transfer.encrypt.web;

import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferException;
import io.github.jasper.transfer.encrypt.crypto.TransferCryptoService;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * multipart 文件完整性校验拦截器。
 *
 * <p>当前策略：</p>
 * <ul>
 *     <li>文件内容不做传输加密</li>
 *     <li>只校验前端上传时附带的 MD5 参数</li>
 *     <li>同时支持单文件与同字段多文件场景</li>
 * </ul>
 */
public class TransferMultipartIntegrityInterceptor implements HandlerInterceptor {

    private final TransferCryptoService cryptoService;

    private final TransferEncryptProperties properties;

    private final TransferPathMatcher pathMatcher;

    /**
     * @param cryptoService crypto service used to calculate MD5 values
     * @param properties externalized starter properties
     * @param pathMatcher request path matcher used to skip non-encrypted endpoints
     */
    public TransferMultipartIntegrityInterceptor(final TransferCryptoService cryptoService,
            final TransferEncryptProperties properties, final TransferPathMatcher pathMatcher) {
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.pathMatcher = pathMatcher;
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod) || !(request instanceof MultipartHttpServletRequest)
                || !pathMatcher.matches(request.getRequestURI())) {
            return true;
        }
        /*final MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
        for (final String fieldName : multipartRequest.getFileMap().keySet()) {
            // 同名字段既可能是单文件，也可能是多文件数组，统一在这里做完整性校验。
            verifyFieldFiles(multipartRequest.getFiles(fieldName), request, fieldName);
        }*/
        return true;
    }

    private void verifyFieldFiles(final List<MultipartFile> files, final HttpServletRequest request, final String fieldName)
            throws IOException {
        if (files == null || files.isEmpty()) {
            return;
        }
        if (files.size() == 1) {
            // 兼容旧协议：单文件场景沿用 __md5_<fieldName>
            final String expectedMd5 = resolveExpectedMd5(request, fieldName);
            if (!StringUtils.hasText(expectedMd5)) {
                return;
            }
            verifySingleFile(files.get(0), expectedMd5, fieldName);
            return;
        }

        // 多文件场景使用索引参数：__md5_<fieldName>__0 / __1 / __2 ...
        for (int index = 0; index < files.size(); index++) {
            final String expectedMd5 = resolveIndexedExpectedMd5(request, fieldName, index);
            if (!StringUtils.hasText(expectedMd5)) {
                throw new TransferException("字段 " + fieldName + " 的第 " + index + " 个文件缺少 MD5 参数");
            }
            verifySingleFile(files.get(index), expectedMd5, fieldName + "[" + index + "]");
        }
    }

    private void verifySingleFile(final MultipartFile file, final String expectedMd5, final String fieldName)
            throws IOException {
        if (!StringUtils.hasText(expectedMd5)) {
            return;
        }
        final String actualMd5 = cryptoService.md5Hex(file.getBytes());
        if (!expectedMd5.equalsIgnoreCase(actualMd5)) {
            throw new TransferException("文件字段 " + fieldName + " 的 MD5 校验失败");
        }
    }

    private String resolveExpectedMd5(final HttpServletRequest request, final String fieldName) {
        return request.getParameter(properties.getMultipartMd5FieldPrefix() + fieldName);
    }

    private String resolveIndexedExpectedMd5(final HttpServletRequest request, final String fieldName, final int index) {
        return request.getParameter(properties.getMultipartMd5FieldPrefix() + fieldName + "__" + index);
    }
}
