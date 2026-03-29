package com.grobird.psf.video.repository;

import com.grobird.psf.video.entity.SkillsetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkillsetRepository extends JpaRepository<SkillsetEntity, Long> {

    /** Tenant filter applied by aspect; order by name. */
    List<SkillsetEntity> findAllByOrderByNameAsc();
}
