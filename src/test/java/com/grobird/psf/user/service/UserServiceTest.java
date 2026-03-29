package com.grobird.psf.user.service;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.config.tenant.TenantContext;
import com.grobird.psf.organization.repository.OrganizationRepository;
import com.grobird.psf.user.dto.UserDTO;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.enums.Role;
import com.grobird.psf.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long ADMIN_ID = 10L;
    private static final Long OTHER_ADMIN_ID = 11L;
    private static final Long SALES_ID = 20L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Create ADMIN (Super Admin or Admin via createUser) ─────────────────

    @Test
    void createUser_adminRole_asAdmin_createsAdmin() {
        UserDTO dto = UserDTO.builder()
                .name("New Admin")
                .email("newadmin@example.com")
                .password("secret")
                .role(Role.ADMIN.name())
                .build();
        UserPrincipal admin = principal(ADMIN_ID, Role.ADMIN.name(), TENANT_ID);
        when(userRepository.existsByTenantIdAndEmailIgnoreCase(TENANT_ID, "newadmin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded");
        UserEntity saved = UserEntity.builder()
                .id(OTHER_ADMIN_ID)
                .tenantId(TENANT_ID)
                .username("New Admin")
                .email("newadmin@example.com")
                .password("encoded")
                .role(Role.ADMIN.name())
                .reportedToUserId(null)
                .build();
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);

        UserDTO result = userService.createUser(dto, admin);

        assertThat(result.getRole()).isEqualTo(Role.ADMIN.name());
        assertThat(result.getEmail()).isEqualTo("newadmin@example.com");
        verify(userRepository).save(any(UserEntity.class));
    }

    private static UserPrincipal principal(Long userId, String role, Long tenantId) {
        return new UserPrincipal(
                userId,
                tenantId,
                "user@example.com",
                role,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
