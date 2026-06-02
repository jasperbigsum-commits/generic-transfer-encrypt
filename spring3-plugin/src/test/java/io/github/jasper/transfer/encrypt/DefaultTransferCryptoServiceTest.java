package io.github.jasper.transfer.encrypt;

import io.github.jasper.transfer.encrypt.config.TransferEncryptProperties;
import io.github.jasper.transfer.encrypt.crypto.DefaultTransferCryptoService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultTransferCryptoServiceTest {

    private static final Sm2TestKeySupport.Sm2KeyPair KEY_PAIR = Sm2TestKeySupport.generateKeyPair();

    @Test
    void shouldRoundTripSm2AndSm4UsingConfiguredKeys() {
        final DefaultTransferCryptoService service = createService(KEY_PAIR);

        final String sm4Key = service.randomSm4Key();
        final String encryptedSm4Key = service.encryptSm2Key(sm4Key);
        final String decryptedSm4Key = service.decryptSm2Key(encryptedSm4Key);
        final String encryptedPayload = service.encryptBySm4("hello-transfer", sm4Key);

        Assertions.assertEquals(sm4Key, decryptedSm4Key);
        Assertions.assertEquals("hello-transfer", service.decryptBySm4(encryptedPayload, sm4Key));
    }

    @Test
    void shouldAcceptSm2CipherTextWithoutUncompressedPointPrefix() {
        final DefaultTransferCryptoService service = createService(KEY_PAIR);
        final String sm4Key = service.randomSm4Key();
        final String encryptedSm4Key = service.encryptSm2Key(sm4Key);

        Assertions.assertTrue(encryptedSm4Key.startsWith("04"));
        Assertions.assertEquals(sm4Key, service.decryptSm2Key(encryptedSm4Key.substring(2)));
    }

    @Test
    void shouldRoundTripSm2UsingRawPrivateKeyAndRawPublicPoint() {
        final DefaultTransferCryptoService service = createService(KEY_PAIR.getRawPrivateKeyHex(),
                KEY_PAIR.getRawPublicKeyHex());
        final String sm4Key = service.randomSm4Key();

        final String encryptedSm4Key = service.encryptSm2Key(sm4Key);

        Assertions.assertEquals(sm4Key, service.decryptSm2Key(encryptedSm4Key));
    }

    @Test
    void shouldEncryptSm2UsingRawPublicPointWithoutPrefix() {
        final DefaultTransferCryptoService service = createService(KEY_PAIR.getRawPrivateKeyHex(),
                KEY_PAIR.getRawPublicKeyHexWithoutPrefix());
        final String sm4Key = service.randomSm4Key();

        final String encryptedSm4Key = service.encryptSm2Key(sm4Key);

        Assertions.assertEquals(sm4Key, service.decryptSm2Key(encryptedSm4Key));
    }

    private DefaultTransferCryptoService createService(final Sm2TestKeySupport.Sm2KeyPair keyPair) {
        return createService(keyPair.getPrivateKeyHex(), keyPair.getPublicKeyHex());
    }

    private DefaultTransferCryptoService createService(final String privateKeyHex, final String publicKeyHex) {
        final TransferEncryptProperties properties = new TransferEncryptProperties();
        properties.setPrivateKey(privateKeyHex);
        properties.setPublicKey(publicKeyHex);
        return new DefaultTransferCryptoService(properties);
    }
}
