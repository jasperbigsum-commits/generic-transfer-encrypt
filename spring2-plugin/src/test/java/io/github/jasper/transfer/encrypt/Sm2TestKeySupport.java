package io.github.jasper.transfer.encrypt;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

final class Sm2TestKeySupport {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm2TestKeySupport() {
    }

    static Sm2KeyPair generateKeyPair() {
        try {
            final KeyPairGenerator keyPairGenerator =
                    KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            keyPairGenerator.initialize(new ECGenParameterSpec("sm2p256v1"), new SecureRandom());
            final KeyPair keyPair = keyPairGenerator.generateKeyPair();
            return new Sm2KeyPair(encodeHex(keyPair.getPrivate().getEncoded()),
                    encodeHex(keyPair.getPublic().getEncoded()));
        } catch (final GeneralSecurityException ex) {
            throw new IllegalStateException("无法生成 SM2 测试密钥对", ex);
        }
    }

    static String encodeHex(final byte[] bytes) {
        final char[] chars = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            final int value = bytes[index] & 0xFF;
            chars[index * 2] = HEX_DIGITS[value >>> 4];
            chars[index * 2 + 1] = HEX_DIGITS[value & 0x0F];
        }
        return new String(chars);
    }

    static final class Sm2KeyPair {

        private final String privateKeyHex;

        private final String publicKeyHex;

        Sm2KeyPair(final String privateKeyHex, final String publicKeyHex) {
            this.privateKeyHex = privateKeyHex;
            this.publicKeyHex = publicKeyHex;
        }

        String getPrivateKeyHex() {
            return privateKeyHex;
        }

        String getPublicKeyHex() {
            return publicKeyHex;
        }
    }
}
