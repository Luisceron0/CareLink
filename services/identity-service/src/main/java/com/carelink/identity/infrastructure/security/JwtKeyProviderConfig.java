package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
public final class JwtKeyProviderConfig {

    /**
     * Creates a production key provider.
     *
     * @return kms or vault provider
     */
    @Bean
    @Profile("prod")
    public JwtKeyProvider vaultKeyProvider() {
        final String kmsUrl = System.getenv("JWT_KMS_SIGNING_URL");
        final String jwks = System.getenv("JWT_JWKS_URL");
        final String kmsKeyId = System.getenv("JWT_KMS_KEY_ID");
        if (kmsUrl != null && !kmsUrl.isBlank()) {
            return new KmsSignerAdapter(kmsUrl, kmsKeyId, jwks);
        }
        return new VaultKeyProvider();
    }

    /**
     * Creates a fallback key provider for non-production environments.
     *
     * @return in-memory key provider
     */
    @Bean
    @ConditionalOnMissingBean(JwtKeyProvider.class)
    public JwtKeyProvider staticKeyProvider() {
        return new StaticKeyProvider();
    }
}
