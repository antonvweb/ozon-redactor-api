package org.ozonLabel.ozonApi.repository;

import org.ozonLabel.ozonApi.entity.DataMatrixFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DataMatrixFileRepository extends JpaRepository<DataMatrixFile, Long> {

    /**
     * Найти все файлы для компании и продукта, отсортированные по дате загрузки (убывание)
     */
    List<DataMatrixFile> findByCompanyIdAndProductIdOrderByUploadedAtDesc(Long companyId, Long productId);

    /**
     * Найти старые файлы (старше 1 года) для компании
     */
    @Query("SELECT f FROM DataMatrixFile f WHERE f.companyId = :companyId AND f.uploadedAt < :cutoff")
    List<DataMatrixFile> findOldFiles(@Param("companyId") Long companyId, @Param("cutoff") LocalDateTime cutoff);
}
