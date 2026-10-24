package com.vidya.security.init;

import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.repository.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initializes and guarantees the existence of the SuperUser in the Master Database.
 * The SuperUser is granted PLATFORM_SUPER_ADMIN, which dynamically inherits all
 * platform roles and permissions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuperUserDataInitializer implements ApplicationRunner {

    private final PlatformUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public static final String SUPERUSER_EMAIL = "superuser@vidya.io";
    public static final String DEFAULT_ADMIN_EMAIL = "admin@vidya.io";

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void run(ApplicationArguments args) {
        ensureSuperUser(SUPERUSER_EMAIL, "USR-SUPERADMIN01", "Super", "User", "+919999999999");
        ensureSuperUser(DEFAULT_ADMIN_EMAIL, "USR-ADMIN01", "Platform", "Admin", "+919999999991");
    }

    private void ensureSuperUser(String email, String userCode, String firstName, String lastName, String phone) {
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(
                user -> {
                    boolean modified = false;
                    if (user.getPlatformRole() != PlatformRole.PLATFORM_SUPER_ADMIN) {
                        user.setPlatformRole(PlatformRole.PLATFORM_SUPER_ADMIN);
                        modified = true;
                    }
                    if (user.getStatus() != UserStatus.ACTIVE) {
                        user.setStatus(UserStatus.ACTIVE);
                        modified = true;
                    }
                    if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
                        user.setFailedLoginAttempts(0);
                        user.setLockedUntil(null);
                        modified = true;
                    }
                    if (user.getPasswordHash() == null || !passwordEncoder.matches("Password@123", user.getPasswordHash())) {
                        user.setPasswordHash(passwordEncoder.encode("Password@123"));
                        modified = true;
                    }
                    if (modified) {
                        userRepository.save(user);
                        log.info("Updated existing user [{}] to ACTIVE PLATFORM_SUPER_ADMIN with verified password", email);
                    }
                },
                () -> {
                    String passwordHash = passwordEncoder.encode("Password@123");
                    PlatformUser newUser = new PlatformUser(
                            userCode,
                            email,
                            passwordHash,
                            firstName,
                            lastName,
                            phone,
                            PlatformRole.PLATFORM_SUPER_ADMIN
                    );
                    userRepository.save(newUser);
                    log.info("Initialized new SuperUser [{}] with full platform roles", email);
                }
        );
    }
}
