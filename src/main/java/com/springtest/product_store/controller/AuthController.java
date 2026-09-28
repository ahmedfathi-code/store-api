package com.springtest.product_store.controller;


import com.springtest.product_store.config.OpenApiConfig;
import com.springtest.product_store.dto.AuthRequest;
import com.springtest.product_store.dto.ChangePasswordRequest;
import com.springtest.product_store.dto.ErrorResponse;
import com.springtest.product_store.dto.RefreshRequest;
import com.springtest.product_store.dto.RegisterRequest;
import com.springtest.product_store.dto.TokenResponse;
import com.springtest.product_store.entity.User;
import com.springtest.product_store.model.Role;
import com.springtest.product_store.repository.UserRepository;
import com.springtest.product_store.security.JwtUtil;
import com.springtest.product_store.security.RefreshTokenService;
import com.springtest.product_store.security.SessionRevocationService;
import com.springtest.product_store.security.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "auth", description = "Register, log in, refresh and log out")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private SessionRevocationService sessionRevocationService;

    // Message in the request's language (Accept-Language: ar -> Arabic, otherwise English)
    private String message(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    private static ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, message, LocalDateTime.now().toString()));
    }

    // New-password minimum by role: USERs as at registration, ADMINs as for the seeded admin
    static final int USER_MIN_PASSWORD_LENGTH = 6;
    static final int ADMIN_MIN_PASSWORD_LENGTH = 12;

    // ✅ Register
    @PostMapping("/register")
    @Operation(summary = "Register a USER account")
    @ApiResponse(responseCode = "201", description = "Registered",
            content = @Content(examples = @ExampleObject(value = "{\"message\":\"Registered successfully\"}")))
    @ApiResponse(responseCode = "400", description = "Invalid email or password",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(examples = @ExampleObject(value = "{\"message\":\"This email is already registered\"}")))
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {

        // نتحقق إن الإيميل مش موجود قبل كده
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("message", message("auth.register.emailTaken")));
        }

        // نعمل الـ User ونحفظه
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ROLE_USER);

        userRepository.save(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("message", message("auth.register.success")));
    }

    // ✅ Login
    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns a 15-minute access token (JWT) and a single-use refresh token.")
    @ApiResponse(responseCode = "200", description = "Logged in",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Wrong email or password",
            content = @Content(examples = @ExampleObject(value = "{\"message\":\"Wrong email or password\"}")))
    @ApiResponse(responseCode = "503", description = "Token store (Redis) unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {

        try {
            // Spring بيتحقق من الإيميل والباسورد تلقائياً
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", message("auth.login.badCredentials")));
        }

        // لو صح، نعمل Token ونبعته
        return ResponseEntity.ok(issueTokens(request.getEmail()));
    }

    // Refresh: exchange a refresh token for a new access + refresh token pair.
    // The old refresh token is consumed (single use), so a leaked one works at most once.
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair",
            description = "Refresh tokens are single use: the one sent here stops working.")
    @ApiResponse(responseCode = "200", description = "New access and refresh tokens",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Missing refresh token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unknown, expired, already used or revoked refresh token",
            content = @Content(examples = @ExampleObject(value = "{\"message\":\"Invalid or expired refresh token\"}")))
    @ApiResponse(responseCode = "503", description = "Token store (Redis) unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        Optional<RefreshTokenService.Consumed> consumed = refreshTokenService.consume(request.getRefreshToken());

        // Unknown/used token, deleted user, or issued before the user's sessions were
        // revoked (e.g. password change): all the same 401
        if (consumed.isEmpty()
                || userRepository.findByEmail(consumed.get().email()).isEmpty()
                || sessionRevocationService.isRevoked(consumed.get().email(), consumed.get().issuedAtMillis())) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", message("auth.refresh.invalid")));
        }

        return ResponseEntity.ok(issueTokens(consumed.get().email()));
    }

    // Logout: revoke the current access token until it would naturally expire,
    // and the refresh token too if one is sent.
    // SecurityConfig guarantees a valid, non-revoked Bearer token reaches this point.
    @PostMapping("/logout")
    @Operation(summary = "Log out",
            description = "Revokes the access token until it expires, and the refresh token too if it is sent.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "Logged out", content = @Content)
    @ApiResponse(responseCode = "401", description = "Missing, invalid, expired or already revoked token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> logout(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
                                       @RequestBody(required = false) RefreshRequest request) {
        String accessToken = authHeader.substring(7);
        tokenBlacklistService.blacklist(accessToken, jwtUtil.getRemainingValidity(accessToken));

        if (request != null && request.getRefreshToken() != null) {
            refreshTokenService.revoke(request.getRefreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    // Change password: requires the current one, then revokes every existing session
    // (all access and refresh tokens issued before now) and returns a fresh token pair
    // so the caller stays logged in.
    @PostMapping("/change-password")
    @Operation(summary = "Change your password",
            description = "Needs the current password. New password: at least 6 characters (USER) or 12 (ADMIN), "
                    + "and different from the current one. Logs out every other session and returns a new token pair.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "200", description = "Password changed; use the new tokens",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Wrong current password, new password too short, or unchanged",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing, invalid, expired or revoked token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Token store (Redis) unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<?> changePassword(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        // The filter only authenticates tokens of existing users
        User user = userRepository.findByEmail(principal.getUsername()).orElseThrow();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            return badRequest(message("auth.password.currentIncorrect"));
        }
        int minLength = user.getRole() == Role.ROLE_ADMIN ? ADMIN_MIN_PASSWORD_LENGTH : USER_MIN_PASSWORD_LENGTH;
        if (request.getNewPassword().length() < minLength) {
            // as text: MessageFormat would localise the digits
            return badRequest(message("auth.password.tooShort", String.valueOf(minLength)));
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            return badRequest(message("auth.password.unchanged"));
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        sessionRevocationService.revokeAllSessions(user.getEmail());

        return ResponseEntity.ok(issueTokens(user.getEmail()));
    }

    private TokenResponse issueTokens(String email) {
        return new TokenResponse(
                jwtUtil.generateToken(email),
                refreshTokenService.issue(email),
                jwtUtil.getAccessTokenValidity().toSeconds()
        );
    }
}