package com.carelink.identity.domain.port;

import com.carelink.identity.domain.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID id);
    void save(User user);
}
