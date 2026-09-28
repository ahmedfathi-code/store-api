package com.springtest.product_store.controller;


import com.springtest.product_store.config.OpenApiConfig;
import com.springtest.product_store.dto.AuthRequest;
import com.springtest.product_store.dto.ErrorResponse;
import com.springtest.product_store.dto.RefreshRequest;
import com.springtest.product_store.dto.RegisterRequest;
import com.springtest.product_store.dto.TokenResponse;
import com.springtest.product_store.entity.User;
import com.springtest.product_store.model.Role;
import com.springtest.product_store.repository.UserRepository;
import com.springtest.product_store.security.JwtUtil;
import com.springtest.product_store.security.RefreshTokenService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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

    // ✅ Register
    @PostMapping("/register")
    @Operation(summary = "Register a USER account")
    @ApiResponse(responseCode = "201", description = "Registered",
            content = @Content(examples = @ExampleObject(value = "{\"message\":\"تم التسجيل بنجاح\"}")))
    @ApiResponse(responseCode = "400", description = "Invalid email or password",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(examples = @ExampleObject(value = "{\"message\":\"الإيميل ده مسجل قبل كده\"}")))
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {

        // نتحقق إن الإيميل مش موجود قبل كده
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "الإيميل ده مسجل قبل كده"));
        }

        // نعمل الـ User ونحفظه
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ROLE_USER);

        userRepository.save(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("message", "تم التسجيل بنجاح"));
    }

    // ✅ Login
    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns a 15-minute access token (JWT) and a single-use refresh token.")
    @ApiResponse(responseCode = "200", description = "Logged in",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Wrong email or password",
            content = @Content(examples = @ExampleObject(value = "{\"message\":\"الإيميل أو الباسورد غلط\"}")))
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
                    .body(Map.of("message", "الإيميل أو الباسورد غلط"));
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
        Optional<String> email = refreshTokenService.consume(request.getRefreshToken());

        if (email.isEmpty() || userRepository.findByEmail(email.get()).isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired refresh token"));
        }

        return ResponseEntity.ok(issueTokens(email.get()));
    }

    // Logout: revoke the current access token until it would naturally expire,
    // and the refresh token too if one is sent.
    // SecurityConfig guarantees a valid, non-revoked Bearer token reaches this point.
    @PostMapping("/logout")
    @Operation(summary = "Log out",
            description = "Revokes the access token until it expires, and the refresh token too if it is sent.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "Logged out", content = @Content)
    @ApiResponse(responseCode = "403", description = "Missing, invalid or already revoked token", content = @Content)
    public ResponseEntity<Void> logout(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
                                       @RequestBody(required = false) RefreshRequest request) {
        String accessToken = authHeader.substring(7);
        tokenBlacklistService.blacklist(accessToken, jwtUtil.getRemainingValidity(accessToken));

        if (request != null && request.getRefreshToken() != null) {
            refreshTokenService.revoke(request.getRefreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    private TokenResponse issueTokens(String email) {
        return new TokenResponse(
                jwtUtil.generateToken(email),
                refreshTokenService.issue(email),
                jwtUtil.getAccessTokenValidity().toSeconds()
        );
    }
}