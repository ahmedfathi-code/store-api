package com.springtest.product_store;

import com.springtest.product_store.entity.User;
import com.springtest.product_store.model.Role;
import com.springtest.product_store.repository.ProductRepository;
import com.springtest.product_store.repository.UserRepository;
import com.springtest.product_store.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

// Full application context against real Postgres (Flyway + ddl-auto=validate)
// and real Redis. Each test starts from empty tables and an empty Redis.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Passw0rd!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtUtil jwtUtil;

    @Autowired
    protected StringRedisTemplate redis;

    @BeforeEach
    void resetState() {
        productRepository.deleteAll();
        userRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // Users are inserted directly: /register can only create ROLE_USER accounts
    protected User createUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        return userRepository.save(user);
    }

    protected String bearer(User user) {
        return "Bearer " + jwtUtil.generateToken(user.getEmail());
    }
}
