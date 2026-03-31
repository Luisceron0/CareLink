package com.carelink.identity.infrastructure.security;

import com.carelink.identity.domain.port.PasswordEncoder;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.stereotype.Component;

@Component
public final class Argon2PasswordEncoder implements PasswordEncoder {

    /** Argon2 iterations. */
    private static final int ITERATIONS = 2;

    /** Argon2 memory in KB. */
    private static final int MEMORY_KB = 65536;

    /** Argon2 parallelism factor. */
    private static final int PARALLELISM = 1;

    /** Argon2 implementation instance. */
    private final Argon2 argon2 = Argon2Factory.create();

    @Override
    public String encode(final CharSequence rawPassword) {
        return argon2.hash(
            ITERATIONS,
            MEMORY_KB,
            PARALLELISM,
            rawPassword.toString().toCharArray()
        );
    }

    @Override
    public boolean matches(
            final CharSequence rawPassword,
            final String encodedPassword) {
        return argon2.verify(
            encodedPassword,
            rawPassword.toString().toCharArray()
        );
    }
}
