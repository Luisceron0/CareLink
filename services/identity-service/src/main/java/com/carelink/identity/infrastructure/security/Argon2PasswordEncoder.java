package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.PasswordEncoder;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.stereotype.Component;

@Component
public class Argon2PasswordEncoder implements PasswordEncoder {
    private final Argon2 argon2 = Argon2Factory.create();

    @Override
    public String encode(CharSequence rawPassword) {
        return argon2.hash(2, 65536, 1, rawPassword.toString().toCharArray());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return argon2.verify(encodedPassword, rawPassword.toString().toCharArray());
    }
}
