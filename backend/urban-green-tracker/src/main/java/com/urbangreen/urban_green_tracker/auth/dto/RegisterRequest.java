package com.urbangreen.urban_green_tracker.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "USERNAME_NOT_BLANK")
    @Size(min = 3, max = 50, message = "USERNAME_INVALID_LENGTH")
    private String username;

    @NotBlank(message = "EMAIL_NOT_BLANK")
    @Email(message = "EMAIL_INVALID_FORMAT")
    private String email;

    @NotBlank(message = "PASSWORD_NOT_BLANK")
    @Size(min = 8, message = "PASSWORD_TOO_SHORT")
    private String password;
}

