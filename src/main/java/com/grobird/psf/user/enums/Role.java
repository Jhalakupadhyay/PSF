package com.grobird.psf.user.enums;

/**
 * Exactly three roles in the system:
 * <ul>
 *   <li><b>SUPER_ADMIN</b> – Single global user, created manually. Not tenant-scoped; can see every tenant and all users.</li>
 *   <li><b>ADMIN</b> – Per tenant; each tenant has several admins.</li>
 *   <li><b>SALES</b> – Per admin; each admin has several sales under them.</li>
 * </ul>
 */
public enum Role {
    SUPER_ADMIN,
    ADMIN,
    SALES;

    /** Roles that can be created via API (Super Admin is created manually only). */
    public static boolean isCreatableViaApi(String role) {
        if (role == null) return false;
        String r = role.toUpperCase();
        return ADMIN.name().equals(r) || SALES.name().equals(r);
    }

    public static Role fromString(String role) {
        if (role == null) throw new IllegalArgumentException("Role cannot be null");
        return Role.valueOf(role.toUpperCase().trim());
    }
}

