package com.carelink.identity.domain.port;

/** Port for password hashing and verification. */
public interface PasswordEncoder {

    /**
     * Encodes a raw password.
     *
     * @param rawPassword raw password
     * @return encoded password
     */
    String encode(CharSequence rawPassword);

    /**
     * Compares raw and encoded password values.
     *
     * @param rawPassword raw password
     * @param encodedPassword encoded password
     * @return true when password matches
     */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}
