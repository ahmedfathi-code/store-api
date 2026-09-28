package com.springtest.product_store.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private JsonErrorWriter jsonErrorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // ✅ بنجيب الـ Authorization header
        String authHeader = request.getHeader("Authorization");

        // لو مفيش header أو مش بيبدأ بـ Bearer، نكمل من غير مصادقة
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ بنشيل الـ Token من بعد كلمة "Bearer "
        String token = authHeader.substring(7);

        // Revoked (logged-out) tokens are treated like invalid ones.
        // If Redis is unreachable we fail closed: a revoked token must never work again.
        boolean revoked;
        try {
            revoked = tokenBlacklistService.isBlacklisted(token);
        } catch (DataAccessException e) {
            jsonErrorWriter.write(request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "auth.unavailable");
            return;
        }

        // ✅ بنتحقق من الـ Token ونجيب الإيميل منه
        if (!revoked && jwtUtil.isTokenValid(token)) {
            String email = jwtUtil.extractEmail(token);

            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // ✅ بنعمل Authentication object ونحطه في الـ SecurityContext
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (UsernameNotFoundException e) {
                // Valid signature, but the user no longer exists: treat like an invalid token (401)
            }
        }

        filterChain.doFilter(request, response);
    }
}