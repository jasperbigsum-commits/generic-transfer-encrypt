package io.github.jasper.transfer.encrypt.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferEnvelopeCodec;
import io.github.jasper.transfer.encrypt.crypto.TransferCryptoService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.DigestUtils;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;
import java.util.Collections;

class TransferEncryptionFilterTest {

    @Test
    void shouldNotResetCommittedResponseWhenClientDisconnectsDuringCopy() {
        final TransferEncryptionFilter filter =
                new TransferEncryptionFilter(new ObjectMapper(), codec(), pathMatcher());
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        final CommitThenFailResponse response = new CommitThenFailResponse();

        Assertions.assertDoesNotThrow(() ->
                filter.doFilterInternal(request, response, (requestToUse, responseToUse) -> {
                    responseToUse.setContentType("application/json");
                    responseToUse.getWriter().write("{\"ok\":true}");
                }));
        Assertions.assertTrue(response.isCommitted());
        Assertions.assertEquals(0, response.getResetBufferAttempts());
    }

    private TransferEnvelopeCodec codec() {
        return new TransferEnvelopeCodec(new NoOpCryptoService());
    }

    private TransferPathMatcher pathMatcher() {
        final TransferEncryptProperties properties = new TransferEncryptProperties();
        properties.setIncludePathRegex(Collections.singletonList("^/api/.*$"));
        return new TransferPathMatcher(properties);
    }

    private static final class NoOpCryptoService implements TransferCryptoService {

        @Override
        public String encryptSm2Key(final String sm4Key) {
            return sm4Key;
        }

        @Override
        public String encryptSm2Key(final String sm4Key, final String publicKey) {
            return sm4Key;
        }

        @Override
        public String decryptSm2Key(final String encryptedKey) {
            return encryptedKey;
        }

        @Override
        public String encryptBySm4(final String plaintext, final String sm4Key) {
            return plaintext;
        }

        @Override
        public String decryptBySm4(final String encryptedText, final String sm4Key) {
            return encryptedText;
        }

        @Override
        public String md5Hex(final byte[] content) {
            return DigestUtils.md5DigestAsHex(content);
        }

        @Override
        public String randomSm4Key() {
            return "test-sm4-key";
        }
    }

    private static final class CommitThenFailResponse extends MockHttpServletResponse {

        private boolean committed;

        private int resetBufferAttempts;

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public void write(final int b) throws IOException {
                    committed = true;
                    throw new IOException("Broken pipe");
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(final WriteListener writeListener) {
                }
            };
        }

        @Override
        public boolean isCommitted() {
            return committed || super.isCommitted();
        }

        @Override
        public void resetBuffer() {
            resetBufferAttempts++;
            if (isCommitted()) {
                throw new IllegalStateException("cannot reset buffer after response has been committed");
            }
            super.resetBuffer();
        }

        private int getResetBufferAttempts() {
            return resetBufferAttempts;
        }
    }
}
