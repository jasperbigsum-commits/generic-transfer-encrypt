package io.github.jasper.transfer.encrypt;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
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
            final ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();
            final ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
            return new Sm2KeyPair(encodeHex(keyPair.getPrivate().getEncoded()),
                    encodeHex(keyPair.getPublic().getEncoded()),
                    encodeFixedLength(privateKey.getS(), 32),
                    "04" + encodeFixedLength(publicKey.getW().getAffineX(), 32)
                            + encodeFixedLength(publicKey.getW().getAffineY(), 32));
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

    private static String encodeFixedLength(final BigInteger value, final int length) {
        final byte[] bytes = value.toByteArray();
        final byte[] normalized = new byte[length];
        final int copyLength = Math.min(bytes.length, length);
        System.arraycopy(bytes, bytes.length - copyLength, normalized, length - copyLength, copyLength);
        return encodeHex(normalized);
    }

    static final class Sm2KeyPair {

        private final String privateKeyHex;

        private final String publicKeyHex;

        private final String rawPrivateKeyHex;

        private final String rawPublicKeyHex;

        Sm2KeyPair(final String privateKeyHex, final String publicKeyHex, final String rawPrivateKeyHex,
                final String rawPublicKeyHex) {
            this.privateKeyHex = privateKeyHex;
            this.publicKeyHex = publicKeyHex;
            this.rawPrivateKeyHex = rawPrivateKeyHex;
            this.rawPublicKeyHex = rawPublicKeyHex;
        }

        String getPrivateKeyHex() {
            return privateKeyHex;
        }

        String getPublicKeyHex() {
            return publicKeyHex;
        }

        String getRawPrivateKeyHex() {
            return rawPrivateKeyHex;
        }

        String getRawPublicKeyHex() {
            return rawPublicKeyHex;
        }

        String getRawPublicKeyHexWithoutPrefix() {
            return rawPublicKeyHex.substring(2);
        }
    }
}
