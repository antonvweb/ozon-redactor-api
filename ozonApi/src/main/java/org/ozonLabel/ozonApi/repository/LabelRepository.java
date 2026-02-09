package org.ozonLabel.ozonApi.repository;

import org.ozonLabel.ozonApi.entity.Label;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LabelRepository extends JpaRepository<Label, Long> {

    Optional<Label> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Label> findByCompanyIdAndProductId(Long companyId, Long productId);

    boolean existsByCompanyIdAndProductId(Long companyId, Long productId);

    Page<Label> findAllByCompanyId(Long companyId, Pageable pageable);

    void deleteAllByCompanyIdAndProductId(Long companyId, Long productId);
}
