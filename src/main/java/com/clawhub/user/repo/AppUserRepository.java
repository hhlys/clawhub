package com.clawhub.user.repo;

import com.clawhub.user.domain.AppUser;
import com.clawhub.user.domain.UserRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(UserRole role);
}
