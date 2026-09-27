package com.springtest.product_store.controller;


import com.springtest.product_store.dto.AuthRequest;
import com.springtest.product_store.dto.RegisterRequest;
import com.springtest.product_store.entity.User;
import com.springtest.product_store.model.Role;
import com.springtest.product_store.repository.UserRepository;
import com.springtest.product_store.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    // ✅ Register
    @PostMapping("/register")
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
        String token = jwtUtil.generateToken(request.getEmail());

        return ResponseEntity.ok(Map.of("token", token));
    }
}