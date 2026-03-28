package com.carelink.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class VaultKeyProviderTest {

    @Test
    void loadsPemAndDerivesPublic() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(priv.getEncoded()) + "\n-----END PRIVATE KEY-----\n";

        VaultKeyProvider provider = new VaultKeyProvider(pem, null, null, null);
        assertTrue(provider.getPrivateKey().isPresent());
        assertTrue(provider.getDefaultKid().isPresent());
        assertTrue(provider.getPublicKeyByKid(provider.getDefaultKid().get()).isPresent());
    }

    @Test
    void loadsFromPemPath() throws Exception {
        java.nio.file.Path p = java.nio.file.Files.createTempFile("test-vault", "pem");
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        java.security.KeyPair kp = kpg.generateKeyPair();
        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(priv.getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        java.nio.file.Files.writeString(p, pem);

        VaultKeyProvider provider = new VaultKeyProvider(null, p.toString(), null, null);
        assertTrue(provider.getPrivateKey().isPresent());
    }
}
