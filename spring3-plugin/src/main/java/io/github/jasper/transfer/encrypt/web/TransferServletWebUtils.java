package io.github.jasper.transfer.encrypt.web;

import io.github.jasper.transfer.encrypt.util.TransferWebUtils;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Servlet-specific helpers for the Spring Boot 3 starter.
 */
final class TransferServletWebUtils {

    private TransferServletWebUtils() {
    }

    static byte[] readBody(final HttpServletRequest request) throws IOException {
        final ServletInputStream inputStream = request.getInputStream();
        return TransferWebUtils.toByteArray(inputStream);
    }

    static Map<String, String[]> extractParameters(final HttpServletRequest request) {
        final Map<String, String[]> parameters = new LinkedHashMap<String, String[]>();
        final Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            final String name = names.nextElement();
            parameters.put(name, request.getParameterValues(name));
        }
        return parameters;
    }
}
