package io.github.jasper.transfer.encrypt.crypto;

import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.springframework.util.StringUtils;

/**
 * 基于 BouncyCastle 的默认国密实现。
 */
public class DefaultTransferCryptoService implements TransferCryptoService {

    private static final char[] RANDOM_CHARS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final TransferEncryptProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();

    public DefaultTransferCryptoService(final TransferEncryptProperties properties) {
        this.properties = properties;
    }

    @Override
    public String encryptSm2Key(final String sm4Key) {
        requirePublicKey();
        return BouncyCastleCryptoSupport.encodeHex(BouncyCastleCryptoSupport.encryptSm2(
                sm4Key.getBytes(StandardCharsets.UTF_8), properties.getPublicKey(), secureRandom));
    }

    @Override
    public String encryptSm2Key(final String sm4Key, final String publicKey) {
        final String effectivePublicKey = StringUtils.hasText(publicKey) ? publicKey : properties.getPublicKey();
        if (!StringUtils.hasText(effectivePublicKey)) {
            throw new TransferException("transfer.encrypt.public-key 未配置");
        }
        return BouncyCastleCryptoSupport.encodeHex(BouncyCastleCryptoSupport.encryptSm2(
                sm4Key.getBytes(StandardCharsets.UTF_8), effectivePublicKey, secureRandom));
    }

    @Override
    public String decryptSm2Key(final String encryptedKey) {
        requirePrivateKey();
        final byte[] decrypted = BouncyCastleCryptoSupport.decryptSm2(
                BouncyCastleCryptoSupport.decodeHex(normalizeSm2Cipher(encryptedKey)), properties.getPrivateKey());
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    @Override
    public String encryptBySm4(final String plaintext, final String sm4Key) {
        return BouncyCastleCryptoSupport.encodeHex(BouncyCastleCryptoSupport.encryptSm4(
                plaintext.getBytes(StandardCharsets.UTF_8), sm4Key.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String decryptBySm4(final String encryptedText, final String sm4Key) {
        return new String(BouncyCastleCryptoSupport.decryptSm4(BouncyCastleCryptoSupport.decodeHex(encryptedText),
                sm4Key.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    @Override
    public String md5Hex(final byte[] content) {
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            return BouncyCastleCryptoSupport.encodeHex(messageDigest.digest(content));
        } catch (final NoSuchAlgorithmException ex) {
            throw new TransferException("JVM 不支持 MD5 算法", ex);
        }
    }

    @Override
    public String randomSm4Key() {
        // 生成固定长度的可打印随机串，直接作为 16 字节 SM4 密钥使用。
        final char[] key = new char[16];
        for (int index = 0; index < key.length; index++) {
            key[index] = RANDOM_CHARS[secureRandom.nextInt(RANDOM_CHARS.length)];
        }
        return new String(key);
    }

    private String normalizeSm2Cipher(final String encryptedKey) {
        if (!StringUtils.hasText(encryptedKey)) {
            throw new TransferException("SM2 密钥交换数据缺失");
        }
        return encryptedKey.startsWith("04") ? encryptedKey : "04" + encryptedKey;
    }

    private void requirePrivateKey() {
        if (!StringUtils.hasText(properties.getPrivateKey())) {
            throw new TransferException("transfer.encrypt.private-key 未配置");
        }
    }

    private void requirePublicKey() {
        if (!StringUtils.hasText(properties.getPublicKey())) {
            throw new TransferException("transfer.encrypt.public-key 未配置");
        }
    }
}
