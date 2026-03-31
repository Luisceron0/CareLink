package com.carelink.identity.infrastructure.persistence;

import com.carelink.identity.domain.User;
import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.value.HashedPassword;
import com.carelink.identity.domain.port.UserRepository;
import com.carelink.identity.infrastructure.persistence.entity.UserEntity;
import com.carelink.identity.infrastructure.persistence.jpa.UserJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public final class JpaUserRepository implements UserRepository {

    /** Spring Data adapter for user persistence. */
    private final UserJpaRepository jpa;

    /**
     * Builds the adapter.
     *
     * @param userJpaRepository spring data repository
     */
    public JpaUserRepository(final UserJpaRepository userJpaRepository) {
        this.jpa = userJpaRepository;
    }

    @Override
    public Optional<User> findByEmail(final String email) {
        return jpa.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<User> findById(final UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public void save(final User user) {
        final UserEntity entity = new UserEntity(
            user.id(),
            user.tenantId(),
            user.email().value(),
            user.role(),
            user.password().value(),
            user.createdAt()
        );
        jpa.save(entity);
    }

    private User toDomain(final UserEntity entity) {
        return new User(
            entity.getId(),
            entity.getTenantId(),
            new Email(entity.getEmail()),
            entity.getRole(),
            new HashedPassword(entity.getPassword()),
            entity.getCreatedAt()
        );
    }
}
