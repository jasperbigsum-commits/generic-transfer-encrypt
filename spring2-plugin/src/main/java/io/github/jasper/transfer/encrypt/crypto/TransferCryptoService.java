package io.github.jasper.transfer.encrypt.crypto;

/**
 * 抽象加密能力接口，便于后续替换国密实现或做测试桩。
 */
public interface TransferCryptoService {

    String encryptSm2Key(String sm4Key);

    String encryptSm2Key(String sm4Key, String publicKey);

    String decryptSm2Key(String encryptedKey);

    String encryptBySm4(String plaintext, String sm4Key);

    String decryptBySm4(String encryptedText, String sm4Key);

    String md5Hex(byte[] content);

    String randomSm4Key();
}
