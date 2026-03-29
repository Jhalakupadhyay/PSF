package com.grobird.psf.config.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationContext;

@Aspect
@Component
public class TenantFilterAspect {

    private final ApplicationContext applicationContext;

    public TenantFilterAspect(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Around("execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..))")
    public Object applyTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        Long tenantId = TenantContext.getTenantId();

        if (tenantId != null) {
            // Pulled fresh each time — avoids the early-lifecycle injection problem
            EntityManager entityManager = applicationContext.getBean(EntityManager.class);
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                    .setParameter("tenantId", tenantId);
        }

        return joinPoint.proceed();
    }
}