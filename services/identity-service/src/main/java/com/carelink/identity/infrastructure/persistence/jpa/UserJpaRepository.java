package com.carelink.identity.infrastructure.persistence.jpa;

import com.carelink.identity.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/** JPA repository for user entities. */
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Finds a user by unique email.
     *
     * @param email email value
     * @return matching user if present
     */
    Optional<UserEntity> findByEmail(String email);
}
