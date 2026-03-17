package org.ozonLabel.ozonApi.repository;

import org.ozonLabel.ozonApi.entity.UserImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserImageRepository extends JpaRepository<UserImage, Long> {

    List<UserImage> findAllByCompanyIdAndUserId(Long companyId, Long userId);

    Optional<UserImage> findByIdAndCompanyIdAndUserId(Long id, Long companyId, Long userId);

    void deleteAllByCompanyIdAndUserId(Long companyId, Long userId);

    @Query("SELECT COALESCE(SUM(ui.sizeBytes), 0) FROM UserImage ui WHERE ui.companyId = :companyId AND ui.userId = :userId")
    long sumSizeBytesByCompanyIdAndUserId(@Param("companyId") Long companyId, @Param("userId") Long userId);

    long countByCompanyIdAndUserId(Long companyId, Long userId);
}
