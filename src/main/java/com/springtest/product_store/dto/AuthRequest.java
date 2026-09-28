package com.springtest.product_store.dto;


import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AuthRequest {

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    private String email;

    @NotBlank(message = "{validation.password.required}")
    private String password;
}