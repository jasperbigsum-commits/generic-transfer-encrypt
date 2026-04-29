package io.github.jasper.transfer.encrypt.crypto;

import io.github.jasper.transfer.encrypt.core.TransferException;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Security;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECPoint;

final class BouncyCastleCryptoSupport {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private static final X9ECParameters SM2_CURVE_PARAMETERS = GMNamedCurves.getByName("sm2p256v1");

    private static final ECDomainParameters SM2_DOMAIN_PARAMETERS =
            new ECDomainParameters(SM2_CURVE_PARAMETERS.getCurve(), SM2_CURVE_PARAMETERS.getG(),
                    SM2_CURVE_PARAMETERS.getN(), SM2_CURVE_PARAMETERS.getH());

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
            engine.init(true, new ParametersWithRandom(loadPublicKey(publicKeyHex), secureRandom));
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
            engine.init(false, loadPrivateKey(privateKeyHex));
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

    private static AsymmetricKeyParameter loadPublicKey(final String publicKeyHex) throws IOException {
        final byte[] keyBytes = decodeHex(publicKeyHex);
        try {
            return PublicKeyFactory.createKey(keyBytes);
        } catch (final IOException ex) {
            return decodeRawPublicKey(keyBytes, ex);
        }
    }

    private static AsymmetricKeyParameter loadPrivateKey(final String privateKeyHex) throws IOException {
        final byte[] keyBytes = decodeHex(privateKeyHex);
        try {
            return PrivateKeyFactory.createKey(keyBytes);
        } catch (final IOException ex) {
            return decodeRawPrivateKey(keyBytes, ex);
        }
    }

    private static ECPublicKeyParameters decodeRawPublicKey(final byte[] keyBytes, final IOException original)
            throws IOException {
        final byte[] normalized = normalizeRawPublicKey(keyBytes, original);
        try {
            final ECPoint point = SM2_DOMAIN_PARAMETERS.getCurve().decodePoint(normalized);
            return new ECPublicKeyParameters(point, SM2_DOMAIN_PARAMETERS);
        } catch (final IllegalArgumentException ex) {
            throw new IOException("SM2 原始公钥点格式非法", ex);
        }
    }

    private static ECPrivateKeyParameters decodeRawPrivateKey(final byte[] keyBytes, final IOException original)
            throws IOException {
        if (keyBytes.length == 0 || keyBytes.length > 32) {
            throw new IOException("SM2 原始私钥长度非法: " + keyBytes.length, original);
        }
        return new ECPrivateKeyParameters(new BigInteger(1, keyBytes), SM2_DOMAIN_PARAMETERS);
    }

    private static byte[] normalizeRawPublicKey(final byte[] keyBytes, final IOException original)
            throws IOException {
        if (keyBytes.length == 64) {
            final byte[] prefixed = new byte[65];
            prefixed[0] = 0x04;
            System.arraycopy(keyBytes, 0, prefixed, 1, keyBytes.length);
            return prefixed;
        }
        if (keyBytes.length == 65 && keyBytes[0] == 0x04) {
            return keyBytes;
        }
        throw new IOException("SM2 原始公钥长度非法: " + keyBytes.length, original);
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
