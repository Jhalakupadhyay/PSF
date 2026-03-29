package com.grobird.psf.organization.repository;

import com.grobird.psf.organization.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, Long> {

    List<OrganizationEntity> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);
}
