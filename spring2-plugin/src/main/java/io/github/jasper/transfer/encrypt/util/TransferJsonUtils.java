package io.github.jasper.transfer.encrypt.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.core.TransferException;

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
}
