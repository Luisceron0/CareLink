package com.carelink.identity.infrastructure.persistence;

import com.carelink.identity.domain.User;
import com.carelink.identity.domain.value.Email;
import com.carelink.identity.domain.port.UserRepository;
import com.carelink.identity.infrastructure.persistence.entity.UserEntity;
import com.carelink.identity.infrastructure.persistence.jpa.UserJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {
    private final UserJpaRepository jpa;

    public JpaUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(e -> new User(e.getId(), e.getTenantId(), new Email(e.getEmail()), e.getRole(), new com.carelink.identity.domain.value.HashedPassword(e.getPassword()), e.getCreatedAt()));
    }

    @Override
    public void save(User user) {
        UserEntity entity = new UserEntity(user.id(), user.tenantId(), user.email().value(), user.role(), user.password().value(), user.createdAt());
        jpa.save(entity);
    }
}
