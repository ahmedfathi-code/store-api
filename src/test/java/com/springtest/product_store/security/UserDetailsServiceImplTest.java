package com.springtest.product_store.security;

import com.springtest.product_store.entity.User;
import com.springtest.product_store.model.Role;
import com.springtest.product_store.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("{bcrypt}hash");
        user.setRole(role);
        return user;
    }

    @Test
    void loadsUserWithRoleAsSingleAuthority() {
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(user("admin@example.com", Role.ROLE_ADMIN)));

        UserDetails details = userDetailsService.loadUserByUsername("admin@example.com");

        assertThat(details.getUsername()).isEqualTo("admin@example.com");
        assertThat(details.getPassword()).isEqualTo("{bcrypt}hash");
        // hasRole("ADMIN") in SecurityConfig matches the "ROLE_ADMIN" authority
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void regularUserGetsRoleUserAuthority() {
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(user("user@example.com", Role.ROLE_USER)));

        assertThat(userDetailsService.loadUserByUsername("user@example.com").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void unknownEmailThrowsUsernameNotFound() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
