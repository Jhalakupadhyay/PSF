package com.grobird.psf.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grobird.psf.user.enums.InvitationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {

    private Long id;
    private String name;
    private String email;
    private String role;
    private String tenantId;
    /** Required when role is SALES: the user id of the ADMIN this user reports to. */
    private Long reportedToUserId;
    /** Request-only (e.g. add-sales); never set in API responses. */
    private String password;
    /** Request-only when creating SALES; never set in API responses. */
    private String firstName;
    private String lastName;

    private String contactNumber;
    private String department;
    private String employeeId;

    /** For SALES users: PENDING until first login, then ACCEPTED. */
    private InvitationStatus invitationStatus;
}
