package com.grobird.psf.video.repository;

import com.grobird.psf.video.entity.ReferenceVideoEntity;
import com.grobird.psf.video.entity.ReferenceVideoType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferenceVideoRepository extends JpaRepository<ReferenceVideoEntity, Long> {

    /** Single golden pitch reference per tenant (with tenant filter). */
    Optional<ReferenceVideoEntity> findFirstByTypeOrderByIdAsc(ReferenceVideoType type);

    /** All reference videos for tenant (with tenant filter). */
    List<ReferenceVideoEntity> findAllByOrderByTypeAscNameAsc();

    /** All reference videos of given type (with tenant filter). */
    List<ReferenceVideoEntity> findAllByTypeOrderByNameAsc(ReferenceVideoType type);

    Optional<ReferenceVideoEntity> findByIdAndType(Long id, ReferenceVideoType type);
}
