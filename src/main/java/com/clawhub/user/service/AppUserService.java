package com.clawhub.user.service;

import com.clawhub.common.error.ConflictException;
import com.clawhub.config.BootstrapAdminProperties;
import com.clawhub.config.BootstrapUserProperties;
import com.clawhub.user.domain.AppUser;
import com.clawhub.user.domain.UserRole;
import com.clawhub.user.domain.UserStatus;
import com.clawhub.user.repo.AppUserRepository;
import com.clawhub.user.web.AdminCreateUserRequest;
import com.clawhub.user.web.RegisterRequest;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties bootstrapAdminProperties;
    private final BootstrapUserProperties bootstrapUserProperties;

    public AppUserService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            BootstrapAdminProperties bootstrapAdminProperties,
            BootstrapUserProperties bootstrapUserProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapAdminProperties = bootstrapAdminProperties;
        this.bootstrapUserProperties = bootstrapUserProperties;
    }

    @PostConstruct
    @Transactional
    public void bootstrapAdminIfNeeded() {
        if (!bootstrapAdminProperties.isEnabled()) {
            return;
        }
        if (userRepository.countByRole(UserRole.ADMIN) > 0) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(bootstrapAdminProperties.getUsername());
        admin.setPasswordHash(passwordEncoder.encode(bootstrapAdminProperties.getPassword()));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);
    }

    @PostConstruct
    @Transactional
    public void bootstrapDefaultUserIfNeeded() {
        if (!bootstrapUserProperties.isEnabled()) {
            return;
        }
        if (userRepository.existsByUsername(bootstrapUserProperties.getUsername())) {
            return;
        }
        AppUser user = new AppUser();
        user.setUsername(bootstrapUserProperties.getUsername());
        user.setPasswordHash(passwordEncoder.encode(bootstrapUserProperties.getPassword()));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<AppUser> listUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AppUser getRequired(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    @Transactional
    public AppUser register(RegisterRequest request) {
        return createUser(request.username(), request.password(), UserRole.USER);
    }

    @Transactional
    public AppUser createByAdmin(AdminCreateUserRequest request) {
        return createUser(request.username(), request.password(), request.role());
    }

    @Transactional
    public AppUser updateRole(Long userId, UserRole role) {
        AppUser user = getRequired(userId);
        user.setRole(role);
        return userRepository.save(user);
    }

    @Transactional
    public AppUser updateStatus(Long userId, UserStatus status) {
        AppUser user = getRequired(userId);
        user.setStatus(status);
        return userRepository.save(user);
    }

    private AppUser createUser(String username, String password, UserRole role) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}
