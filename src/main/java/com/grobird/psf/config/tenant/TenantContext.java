package com.grobird.psf.config.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /** Must be called at the end of every request — prevents leaks in thread pools */
    public static void clear() {
        TENANT_ID.remove();
    }
}