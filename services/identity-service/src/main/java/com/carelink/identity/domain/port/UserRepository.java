package com.carelink.identity.domain.port;

import com.carelink.identity.domain.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);
    void save(User user);
}
