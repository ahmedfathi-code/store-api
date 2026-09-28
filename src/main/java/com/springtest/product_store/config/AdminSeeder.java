package com.springtest.product_store.config;

import com.springtest.product_store.entity.User;
import com.springtest.product_store.model.Role;
import com.springtest.product_store.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

// Creates an ADMIN account from ADMIN_EMAIL/ADMIN_PASSWORD on startup, since /register
// can only create USERs. Create-only: it never promotes an existing user and never
// changes an existing password. Misconfiguration stops the app from starting.
@Component
public class AdminSeeder implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.email:}") String email,
                       @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim();
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean hasEmail = !email.isEmpty();
        boolean hasPassword = !password.isEmpty();

        if (!hasEmail && !hasPassword) {
            return; // seeding not configured
        }
        if (hasEmail != hasPassword) {
            throw new IllegalStateException("ADMIN_EMAIL and ADMIN_PASSWORD must be set together, or both left empty");
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalStateException("ADMIN_EMAIL is not a valid email address");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() == Role.ROLE_ADMIN) {
                log.info("Admin account {} already exists; leaving it unchanged", email);
            } else {
                log.warn("ADMIN_EMAIL {} belongs to an existing {} account; not promoting it. "
                        + "Promote it explicitly if that is intended.", email, existing.get().getRole());
            }
            return;
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(Role.ROLE_ADMIN);
        try {
            userRepository.save(admin);
            log.info("Created admin account {}", email);
        } catch (DataIntegrityViolationException e) {
            // Another instance starting at the same time created it first
            log.info("Admin account {} was created concurrently; leaving it unchanged", email);
        }
    }
}
