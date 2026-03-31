package com.carelink.identity.domain.port;

import com.carelink.identity.domain.User;
import java.util.Optional;
import java.util.UUID;

/** Port for user persistence operations. */
public interface UserRepository {

    /**
     * Finds a user by email.
     *
     * @param email user email
     * @return matching user if present
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by id.
     *
     * @param id user id
     * @return matching user if present
     */
    Optional<User> findById(UUID id);

    /**
     * Saves a user.
     *
     * @param user user aggregate
     */
    void save(User user);
}
