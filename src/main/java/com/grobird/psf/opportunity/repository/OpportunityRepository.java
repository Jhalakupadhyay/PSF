package com.grobird.psf.opportunity.repository;

import com.grobird.psf.opportunity.entity.OpportunityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OpportunityRepository extends JpaRepository<OpportunityEntity, Long> {

    List<OpportunityEntity> findBySales_Id(Long salesUserId);

    boolean existsByIdAndSales_Id(Long opportunityId, Long salesUserId);

    /** All opportunities belonging to sales that report to this admin. */
    List<OpportunityEntity> findBySales_ReportedToUserId(Long adminUserId);

    /** Opportunities for a specific sales user who reports to the given admin. */
    List<OpportunityEntity> findBySales_IdAndSales_ReportedToUserId(Long salesUserId, Long adminUserId);

    boolean existsByIdAndSales_ReportedToUserId(Long opportunityId, Long adminUserId);

    /** One opportunity per (sales, company, industry) — case-insensitive. */
    boolean existsBySales_IdAndCompanyIgnoreCaseAndIndustryIgnoreCase(Long salesId, String company, String industry);

    /** Count opportunities in a tenant (for Super Admin org summary). */
    long countByTenantId(Long tenantId);

    /** Get latest opportunity created_at per sales user for a list of sales user IDs. */
    @Query("SELECT o.sales.id, MAX(o.createdAt) FROM OpportunityEntity o " +
           "WHERE o.sales.id IN :salesUserIds GROUP BY o.sales.id")
    List<Object[]> findLatestOpportunityDatesBySalesUserIds(List<Long> salesUserIds);
}
