package com.springtest.product_store.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

// "token" keeps the original login response field name, so existing clients keep working
@Getter
@AllArgsConstructor
public class TokenResponse {

    private String token;
    private String refreshToken;

    // Access-token lifetime in seconds
    private long expiresIn;
}
