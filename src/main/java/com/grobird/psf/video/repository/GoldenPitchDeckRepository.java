package com.grobird.psf.video.repository;

import com.grobird.psf.video.entity.GoldenPitchDeckEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GoldenPitchDeckRepository extends JpaRepository<GoldenPitchDeckEntity, Long> {

    /** With tenant filter applied, at most one row per tenant. */
    Optional<GoldenPitchDeckEntity> findFirstByOrderByIdAsc();
}
