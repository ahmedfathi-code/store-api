package com.springtest.product_store.dto;


import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AuthRequest {

    @NotBlank(message = "الإيميل مطلوب")
    @Email(message = "إيميل مش صحيح")
    private String email;

    @NotBlank(message = "الباسورد مطلوب")
    private String password;
}