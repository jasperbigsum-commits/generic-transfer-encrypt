package io.github.jasper.transfer.encrypt.crypto;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.SM2;
import cn.hutool.crypto.symmetric.SM4;
import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.core.TransferException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 基于 Hutool + BouncyCastle 的默认国密实现。
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
        final byte[] encrypted = sm2(properties.getPublicKey()).encrypt(sm4Key.getBytes(StandardCharsets.UTF_8),
                KeyType.PublicKey);
        return HexUtil.encodeHexStr(encrypted);
    }

    @Override
    public String encryptSm2Key(final String sm4Key, final String publicKey) {
        final String effectivePublicKey = StrUtil.blankToDefault(publicKey, properties.getPublicKey());
        if (StrUtil.isBlank(effectivePublicKey)) {
            throw new TransferException("transfer.encrypt.public-key 未配置");
        }
        final byte[] encrypted = sm2(effectivePublicKey).encrypt(sm4Key.getBytes(StandardCharsets.UTF_8),
                KeyType.PublicKey);
        return HexUtil.encodeHexStr(encrypted);
    }

    @Override
    public String decryptSm2Key(final String encryptedKey) {
        requirePrivateKey();
        final byte[] decrypted = sm2().decrypt(HexUtil.decodeHex(normalizeSm2Cipher(encryptedKey)), KeyType.PrivateKey);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    @Override
    public String encryptBySm4(final String plaintext, final String sm4Key) {
        return sm4(sm4Key).encryptHex(plaintext);
    }

    @Override
    public String decryptBySm4(final String encryptedText, final String sm4Key) {
        return sm4(sm4Key).decryptStr(encryptedText, StandardCharsets.UTF_8);
    }

    @Override
    public String md5Hex(final byte[] content) {
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            return HexUtil.encodeHexStr(messageDigest.digest(content));
        } catch (final NoSuchAlgorithmException ex) {
            throw new TransferException("JVM 不支持 MD5 算法", ex);
        }
    }

    @Override
    public String randomSm4Key() {
        // 生成固定长度的可打印随机串，直接用于 Hutool SM4 的 16 字节密钥。
        final char[] key = new char[16];
        for (int index = 0; index < key.length; index++) {
            key[index] = RANDOM_CHARS[secureRandom.nextInt(RANDOM_CHARS.length)];
        }
        return new String(key);
    }

    private SM2 sm2() {
        return SmUtil.sm2(StrUtil.emptyToDefault(properties.getPrivateKey(), null),
                StrUtil.emptyToDefault(properties.getPublicKey(), null));
    }

    private SM2 sm2(final String publicKey) {
        return SmUtil.sm2(StrUtil.emptyToDefault(properties.getPrivateKey(), null),
                StrUtil.emptyToDefault(publicKey, null));
    }

    private SM4 sm4(final String sm4Key) {
        return SmUtil.sm4(sm4Key.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeSm2Cipher(final String encryptedKey) {
        if (StrUtil.isBlank(encryptedKey)) {
            throw new TransferException("SM2 密钥交换数据缺失");
        }
        return encryptedKey.startsWith("04") ? encryptedKey : "04" + encryptedKey;
    }

    private void requirePrivateKey() {
        if (StrUtil.isBlank(properties.getPrivateKey())) {
            throw new TransferException("transfer.encrypt.private-key 未配置");
        }
    }

    private void requirePublicKey() {
        if (StrUtil.isBlank(properties.getPublicKey())) {
            throw new TransferException("transfer.encrypt.public-key 未配置");
        }
    }
}
