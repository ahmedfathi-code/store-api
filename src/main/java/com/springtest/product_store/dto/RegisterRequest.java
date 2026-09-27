package com.springtest.product_store.dto;


import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "الإيميل مطلوب")
    @Email(message = "إيميل مش صحيح")
    private String email;

    @NotBlank(message = "الباسورد مطلوب")
    @Size(min = 6, message = "الباسورد لازم يكون 6 حروف على الأقل")
    private String password;
}