package com.springtest.product_store.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// No @Data: a generated toString() would leak the token into logs
@Getter
@Setter
public class RefreshRequest {

    @NotBlank(message = "{validation.refreshToken.required}")
    private String refreshToken;
}
