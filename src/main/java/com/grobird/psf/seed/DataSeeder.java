package com.grobird.psf.seed;

import com.grobird.psf.organization.entity.OrganizationEntity;
import com.grobird.psf.organization.repository.OrganizationRepository;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.enums.Role;
import com.grobird.psf.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Seeds the DB with one Super Admin, one Admin, and one Sales user for testing.
 * Run with: --spring.profiles.active=seed (or include "seed" in active profiles).
 * All test accounts use password: Password1!
 */
@Component
@Profile("seed")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final String TEST_PASSWORD = "Password1!";
    public static final String SUPER_ADMIN_EMAIL = "superadmin@test.com";
    public static final String ADMIN_EMAIL = "admin@test.com";
    public static final String SALES_EMAIL = "sales@test.com";

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(OrganizationRepository organizationRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.findByEmail(SUPER_ADMIN_EMAIL).isPresent()) {
            log.info("Seed data already present (superadmin exists). Skipping seed.");
            return;
        }

        String encodedPassword = passwordEncoder.encode(TEST_PASSWORD);

        UserEntity superAdmin = UserEntity.builder()
                .tenantId(null)
                .username("Super Admin")
                .email(SUPER_ADMIN_EMAIL)
                .password(encodedPassword)
                .role(Role.SUPER_ADMIN.name())
                .reportedToUserId(null)
                .build();
        superAdmin = userRepository.save(superAdmin);
        log.info("Created Super Admin: {} (id={})", SUPER_ADMIN_EMAIL, superAdmin.getId());

        OrganizationEntity org = OrganizationEntity.builder()
                .companyName("Rubrik")
                .industry("Technology")
                .createdAt(Instant.now())
                .build();
        org = organizationRepository.save(org);
        Long tenantId = org.getId();
        log.info("Created Organization: {} (id={})", org.getCompanyName(), tenantId);

        // Extra organizations with past createdAt for dashboard/charts (daily, weekly, monthly stats)
        List<OrganizationEntity> extraOrgs = List.of(
                orgWithCreatedAt("Alpha Corp", "Finance", Instant.now().minus(1, ChronoUnit.DAYS)),
                orgWithCreatedAt("Beta Inc", "Healthcare", Instant.now().minus(2, ChronoUnit.DAYS)),
                orgWithCreatedAt("Gamma Ltd", "Retail", Instant.now().minus(3, ChronoUnit.DAYS)),
                orgWithCreatedAt("Delta Co", "Technology", Instant.now().minus(5, ChronoUnit.DAYS)),
                orgWithCreatedAt("Epsilon LLC", "Manufacturing", Instant.now().minus(7, ChronoUnit.DAYS)),
                orgWithCreatedAt("Zeta Industries", "Energy", Instant.now().minus(14, ChronoUnit.DAYS)),
                orgWithCreatedAt("Eta Solutions", "Technology", Instant.now().minus(21, ChronoUnit.DAYS)),
                orgWithCreatedAt("Theta Group", "Consulting", Instant.now().minus(45, ChronoUnit.DAYS)),
                orgWithCreatedAt("Iota Systems", "Technology", Instant.now().minus(60, ChronoUnit.DAYS)),
                orgWithCreatedAt("Kappa Partners", "Finance", Instant.now().minus(90, ChronoUnit.DAYS))
        );
        for (OrganizationEntity e : extraOrgs) {
            organizationRepository.save(e);
        }
        log.info("Created {} extra organizations with varied createdAt for stats/charts", extraOrgs.size());

        UserEntity admin = UserEntity.builder()
                .tenantId(tenantId)
                .username("Test Admin")
                .email(ADMIN_EMAIL)
                .password(encodedPassword)
                .role(Role.ADMIN.name())
                .reportedToUserId(null)
                .build();
        admin = userRepository.save(admin);
        log.info("Created Admin: {} (id={})", ADMIN_EMAIL, admin.getId());

        UserEntity sales = UserEntity.builder()
                .tenantId(tenantId)
                .username("Test Sales")
                .email(SALES_EMAIL)
                .password(encodedPassword)
                .role(Role.SALES.name())
                .reportedToUserId(admin.getId())
                .contactNumber("+1234567890")
                .department("Sales")
                .employeeId("EMP001")
                .build();
        sales = userRepository.save(sales);
        log.info("Created Sales: {} (id={}, reports to admin id={})", SALES_EMAIL, sales.getId(), admin.getId());

        log.info("Seed complete. Login with: {} / {} / {} — password: {}", SUPER_ADMIN_EMAIL, ADMIN_EMAIL, SALES_EMAIL, TEST_PASSWORD);
    }

    private static OrganizationEntity orgWithCreatedAt(String companyName, String industry, Instant createdAt) {
        return OrganizationEntity.builder()
                .companyName(companyName)
                .industry(industry)
                .createdAt(createdAt)
                .build();
    }
}
