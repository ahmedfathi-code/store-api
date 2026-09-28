package com.springtest.product_store.config;

import com.springtest.product_store.entity.User;
import com.springtest.product_store.model.Role;
import com.springtest.product_store.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD = "a-strong-admin-pass";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminSeeder seeder(String email, String password) {
        return new AdminSeeder(userRepository, passwordEncoder, email, password);
    }

    private static User existing(Role role) {
        User user = new User();
        user.setEmail(EMAIL);
        user.setPassword("{bcrypt}old-hash");
        user.setRole(role);
        return user;
    }

    @Test
    void createsAdminWithEncodedPasswordWhenAbsent() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("{bcrypt}new-hash");

        seeder(EMAIL, PASSWORD).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(saved.getValue().getPassword()).isEqualTo("{bcrypt}new-hash").isNotEqualTo(PASSWORD);
    }

    @Test
    void trimsEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        seeder("  " + EMAIL + " ", PASSWORD).run(null);

        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void leavesExistingAdminUnchanged() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing(Role.ROLE_ADMIN)));

        seeder(EMAIL, PASSWORD).run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void neverPromotesAnExistingUser() {
        User user = existing(Role.ROLE_USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        seeder(EMAIL, PASSWORD).run(null);

        verify(userRepository, never()).save(any());
        assertThat(user.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(user.getPassword()).isEqualTo("{bcrypt}old-hash");
    }

    @Test
    void doesNothingWhenNotConfigured() {
        seeder("", "").run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void refusesToStartWithOnlyEmail() {
        assertThatThrownBy(() -> seeder(EMAIL, "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set together");
        verifyNoInteractions(userRepository);
    }

    @Test
    void refusesToStartWithOnlyPassword() {
        assertThatThrownBy(() -> seeder("", PASSWORD).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set together");
    }

    @Test
    void refusesToStartWithInvalidEmail() {
        assertThatThrownBy(() -> seeder("not-an-email", PASSWORD).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid email");
        verifyNoInteractions(userRepository);
    }

    @Test
    void refusesToStartWithShortPassword() {
        String elevenChars = "abcdefghijk";
        assertThatThrownBy(() -> seeder(EMAIL, elevenChars).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12 characters")
                .hasMessageNotContaining(elevenChars);
        verifyNoInteractions(userRepository);
    }

    @Test
    void acceptsExactlyTwelveCharacters() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatCode(() -> seeder(EMAIL, "abcdefghijkl").run(null)).doesNotThrowAnyException();
    }

    @Test
    void toleratesConcurrentCreationByAnotherInstance() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("users_email_key"));

        assertThatCode(() -> seeder(EMAIL, PASSWORD).run(null)).doesNotThrowAnyException();
    }
}
