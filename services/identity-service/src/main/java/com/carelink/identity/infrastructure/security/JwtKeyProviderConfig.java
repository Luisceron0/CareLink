package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import com.carelink.identity.infrastructure.security.KmsSignerAdapter;

@Configuration
public class JwtKeyProviderConfig {

    @Bean
    @Profile("prod")
    public JwtKeyProvider vaultKeyProvider() {
        // Prefer a KMS sign-only adapter when configured via env var
        String kmsUrl = System.getenv("JWT_KMS_SIGNING_URL");
        String jwks = System.getenv("JWT_JWKS_URL");
        String kmsKeyId = System.getenv("JWT_KMS_KEY_ID");
        if (kmsUrl != null && !kmsUrl.isBlank()) {
            return new KmsSignerAdapter(kmsUrl, kmsKeyId, jwks);
        }
        return new VaultKeyProvider();
    }

    @Bean
    @ConditionalOnMissingBean(JwtKeyProvider.class)
    public JwtKeyProvider staticKeyProvider() {
        return new StaticKeyProvider();
    }
}
