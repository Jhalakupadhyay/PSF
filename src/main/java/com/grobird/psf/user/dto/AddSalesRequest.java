package com.grobird.psf.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body when an ADMIN adds a sales person under them.
 * Username is built from firstName + lastName.
 * Password is auto-generated and sent via email to the sales user.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddSalesRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "Contact number is required")
    private String contactNumber;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Employee ID is required")
    private String employeeId;
}
