package com.springtest.product_store.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// No @Data: a generated toString() would leak both passwords into logs.
// The minimum length depends on the user's role, so it's checked in the controller.
@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "{validation.currentPassword.required}")
    private String currentPassword;

    @NotBlank(message = "{validation.newPassword.required}")
    private String newPassword;
}
