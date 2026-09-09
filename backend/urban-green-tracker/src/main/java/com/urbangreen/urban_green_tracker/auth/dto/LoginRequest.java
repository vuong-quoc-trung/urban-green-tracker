package com.urbangreen.urban_green_tracker.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class LoginRequest {
    @NotBlank( message = "EMAIL_OR_USERNAME_NOT_BLANK")
    private String identifier;

    @NotBlank( message = "PASSWORD_NOT_BLANK")
    private String password;
}
