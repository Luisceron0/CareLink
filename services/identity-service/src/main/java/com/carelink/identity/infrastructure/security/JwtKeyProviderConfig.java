package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.JwtKeyProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class JwtKeyProviderConfig {

    @Bean
    @Profile("prod")
    public JwtKeyProvider vaultKeyProvider() {
        return new VaultKeyProvider();
    }

    @Bean
    @ConditionalOnMissingBean(JwtKeyProvider.class)
    public JwtKeyProvider staticKeyProvider() {
        return new StaticKeyProvider();
    }
}
