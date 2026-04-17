package io.github.jasper.transfer.encrypt.util;

import io.github.jasper.transfer.encrypt.core.TransferConstants;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

/**
 * Web 层通用辅助方法。
 *
 * <p>主要处理：</p>
 * <ul>
 *     <li>请求体读取</li>
 *     <li>Content-Type 判断</li>
 *     <li>query string / parameterMap 互转</li>
 *     <li>文件/二进制响应识别</li>
 * </ul>
 */
public final class TransferWebUtils {

    private TransferWebUtils() {
    }

    public static byte[] toByteArray(final InputStream inputStream) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }

    public static byte[] readBody(final HttpServletRequest request) throws IOException {
        final ServletInputStream inputStream = request.getInputStream();
        return toByteArray(inputStream);
    }

    public static boolean isJsonContentType(final String contentType) {
        return containsContentType(contentType, MediaType.APPLICATION_JSON_VALUE);
    }

    public static boolean isFormContentType(final String contentType) {
        return containsContentType(contentType, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    public static boolean isMultipartContentType(final String contentType) {
        return containsContentType(contentType, MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    public static boolean isBinaryResponse(final String contentType, final String contentDisposition) {
        if (StringUtils.hasText(contentDisposition) && contentDisposition.toLowerCase().contains("attachment")) {
            return true;
        }
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        final String normalized = contentType.toLowerCase();
        return normalized.contains("octet-stream") || normalized.startsWith("image/")
                || normalized.startsWith("video/") || normalized.startsWith("audio/") || normalized.contains("pdf")
                || normalized.contains("zip") || normalized.contains("excel") || normalized.contains("word")
                || normalized.contains("powerpoint") || normalized.contains("download");
    }

    public static boolean isTransferEnvelopeJson(final String payload) {
        return StringUtils.hasText(payload)
                && payload.contains("\"" + TransferConstants.FIELD_TRANSFER_PAYLOAD + "\"");
    }

    public static String normalizeContentType(final String contentType) {
        return StringUtils.hasText(contentType) ? contentType : MediaType.APPLICATION_JSON_VALUE;
    }

    public static Map<String, String[]> parseQueryString(final String queryString) {
        final Map<String, List<String>> values = new LinkedHashMap<String, List<String>>();
        if (!StringUtils.hasText(queryString)) {
            return Collections.emptyMap();
        }
        final String[] pairs = queryString.split("&");
        for (final String pair : pairs) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            final int separatorIndex = pair.indexOf('=');
            final String key = separatorIndex >= 0 ? pair.substring(0, separatorIndex) : pair;
            final String value = separatorIndex >= 0 ? pair.substring(separatorIndex + 1) : "";
            final String decodedKey = decode(key);
            final String decodedValue = decode(value);
            values.computeIfAbsent(decodedKey, item -> new ArrayList<>()).add(decodedValue);
        }
        final Map<String, String[]> parameterMap = new LinkedHashMap<String, String[]>();
        for (final Map.Entry<String, List<String>> entry : values.entrySet()) {
            parameterMap.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        return parameterMap;
    }

    public static String toQueryString(final Map<String, List<String>> queryParameters) {
        final StringBuilder builder = new StringBuilder();
        for (final Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            for (final String value : entry.getValue()) {
                if (builder.length() > 0) {
                    builder.append('&');
                }
                builder.append(encode(entry.getKey())).append('=').append(encode(value));
            }
        }
        return builder.toString();
    }

    public static Map<String, String[]> extractParameters(final HttpServletRequest request) {
        final Map<String, String[]> parameters = new LinkedHashMap<String, String[]>();
        final Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            final String name = names.nextElement();
            parameters.put(name, request.getParameterValues(name));
        }
        return parameters;
    }

    private static boolean containsContentType(final String contentType, final String candidate) {
        return StringUtils.hasText(contentType) && contentType.toLowerCase().contains(candidate.toLowerCase());
    }

    @SneakyThrows
    private static String encode(final String source) {
        return URLEncoder.encode(source, "UTF-8");
    }

    @SneakyThrows
    private static String decode(final String source) {
        return URLDecoder.decode(source, "UTF-8");
    }
}
