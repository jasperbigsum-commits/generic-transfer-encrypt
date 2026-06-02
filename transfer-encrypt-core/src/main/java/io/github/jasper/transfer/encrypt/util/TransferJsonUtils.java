package io.github.jasper.transfer.encrypt.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.core.TransferException;
import io.github.jasper.transfer.encrypt.model.TransferEnvelope;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * JSON 读写辅助工具，统一把底层序列化异常转换为传输层异常。
 */
public final class TransferJsonUtils {

    private TransferJsonUtils() {
    }

    public static <T> T readValue(final ObjectMapper objectMapper, final String payload, final Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (final Exception ex) {
            throw new TransferException("JSON 解析失败", ex);
        }
    }

    public static byte[] writeBytes(final ObjectMapper objectMapper, final Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (final JsonProcessingException ex) {
            throw new TransferException("JSON 序列化失败", ex);
        }
    }

    public static String writeString(final ObjectMapper objectMapper, final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (final JsonProcessingException ex) {
            throw new TransferException("JSON 序列化失败", ex);
        }
    }

    public static String encodeTransportPayload(final ObjectMapper objectMapper, final TransferEnvelope envelope) {
        final byte[] bytes = writeBytes(objectMapper, envelope);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static TransferEnvelope decodeTransportPayload(final ObjectMapper objectMapper, final String payload) {
        try {
            final byte[] bytes = Base64.getUrlDecoder().decode(normalizeBase64Padding(payload));
            return objectMapper.readValue(new String(bytes, StandardCharsets.UTF_8), TransferEnvelope.class);
        } catch (final Exception ex) {
            throw new TransferException("传输层信封载荷解析失败", ex);
        }
    }

    private static String normalizeBase64Padding(final String payload) {
        final int mod = payload.length() % 4;
        if (mod == 0) {
            return payload;
        }
        final StringBuilder builder = new StringBuilder(payload);
        for (int index = mod; index < 4; index++) {
            builder.append('=');
        }
        return builder.toString();
    }
}
