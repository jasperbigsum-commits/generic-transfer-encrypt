package io.github.jasper.transfer.encrypt.crypto;

import io.github.jasper.transfer.encrypt.core.TransferException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Security;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

final class BouncyCastleCryptoSupport {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private BouncyCastleCryptoSupport() {
    }

    static byte[] encryptSm2(final byte[] plaintext, final String publicKeyHex, final SecureRandom secureRandom) {
        try {
            final SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(true, new ParametersWithRandom(PublicKeyFactory.createKey(decodeHex(publicKeyHex)),
                    secureRandom));
            return engine.processBlock(plaintext, 0, plaintext.length);
        } catch (final IOException ex) {
            throw new TransferException("SM2 公钥格式非法", ex);
        } catch (final InvalidCipherTextException ex) {
            throw new TransferException("SM2 加密失败", ex);
        }
    }

    static byte[] decryptSm2(final byte[] ciphertext, final String privateKeyHex) {
        try {
            final SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(false, PrivateKeyFactory.createKey(decodeHex(privateKeyHex)));
            return engine.processBlock(ciphertext, 0, ciphertext.length);
        } catch (final IOException ex) {
            throw new TransferException("SM2 私钥格式非法", ex);
        } catch (final InvalidCipherTextException ex) {
            throw new TransferException("SM2 解密失败", ex);
        }
    }

    static byte[] encryptSm4(final byte[] plaintext, final byte[] key) {
        return doSm4(Cipher.ENCRYPT_MODE, plaintext, key);
    }

    static byte[] decryptSm4(final byte[] ciphertext, final byte[] key) {
        return doSm4(Cipher.DECRYPT_MODE, ciphertext, key);
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

    static byte[] decodeHex(final String hex) {
        if (hex == null) {
            throw new TransferException("十六进制数据缺失");
        }
        if ((hex.length() & 1) != 0) {
            throw new TransferException("十六进制数据长度非法");
        }
        final byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < hex.length(); index += 2) {
            final int high = Character.digit(hex.charAt(index), 16);
            final int low = Character.digit(hex.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw new TransferException("十六进制数据格式非法");
            }
            bytes[index / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    private static byte[] doSm4(final int mode, final byte[] content, final byte[] key) {
        try {
            final Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS5Padding", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(mode, new SecretKeySpec(key, "SM4"));
            return cipher.doFinal(content);
        } catch (final GeneralSecurityException ex) {
            throw new TransferException("SM4 处理失败", ex);
        }
    }
}
