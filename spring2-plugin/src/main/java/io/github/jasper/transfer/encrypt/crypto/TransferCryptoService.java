package io.github.jasper.transfer.encrypt.crypto;

/**
 * 抽象加密能力接口，便于后续替换国密实现或做测试桩。
 */
public interface TransferCryptoService {

    /**
     * Encrypts a per-request SM4 key with the default configured SM2 public key.
     *
     * @param sm4Key plaintext SM4 key
     * @return hexadecimal SM2 cipher text
     */
    String encryptSm2Key(String sm4Key);

    /**
     * Encrypts a per-request SM4 key with an explicit downstream SM2 public key.
     *
     * @param sm4Key plaintext SM4 key
     * @param publicKey downstream SM2 public key override
     * @return hexadecimal SM2 cipher text
     */
    String encryptSm2Key(String sm4Key, String publicKey);

    /**
     * Decrypts the SM4 key contained in an inbound request envelope.
     *
     * @param encryptedKey hexadecimal SM2 cipher text
     * @return plaintext SM4 key
     */
    String decryptSm2Key(String encryptedKey);

    /**
     * Encrypts plaintext with SM4.
     *
     * @param plaintext original content
     * @param sm4Key plaintext SM4 key
     * @return encrypted text
     */
    String encryptBySm4(String plaintext, String sm4Key);

    /**
     * Decrypts SM4 ciphertext.
     *
     * @param encryptedText SM4 ciphertext
     * @param sm4Key plaintext SM4 key
     * @return decrypted plaintext
     */
    String decryptBySm4(String encryptedText, String sm4Key);

    /**
     * Computes the hexadecimal MD5 of the supplied bytes.
     *
     * @param content raw content bytes
     * @return hexadecimal MD5 string
     */
    String md5Hex(byte[] content);

    /**
     * @return a new random SM4 key for a single transport exchange
     */
    String randomSm4Key();
}
