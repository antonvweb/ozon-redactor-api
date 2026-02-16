package org.ozonLabel.user.repository;

import org.ozonLabel.user.entity.LabelSize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabelSizeRepository extends JpaRepository<LabelSize, Long> {

    /**
     * Получить все шаблоны компании + системные шаблоны
     */
    @Query("SELECT ls FROM LabelSize ls WHERE ls.companyId = :companyId OR ls.companyId = 0 ORDER BY ls.isSystem DESC, ls.name ASC")
    List<LabelSize> findAllByCompanyIdOrSystem(@Param("companyId") Long companyId);

    /**
     * Получить только шаблоны компании (без системных)
     */
    List<LabelSize> findByCompanyIdOrderByNameAsc(Long companyId);

    /**
     * Найти шаблон по ID и компании
     */
    Optional<LabelSize> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Проверить существование шаблона с таким именем в компании
     */
    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    /**
     * Сбросить флаг isDefault для всех шаблонов компании
     */
    @Modifying
    @Query("UPDATE LabelSize ls SET ls.isDefault = false WHERE ls.companyId = :companyId AND ls.isDefault = true")
    void resetDefaultForCompany(@Param("companyId") Long companyId);

    /**
     * Получить шаблон по умолчанию для компании
     */
    Optional<LabelSize> findByCompanyIdAndIsDefaultTrue(Long companyId);

    /**
     * Удалить все шаблоны компании (кроме системных)
     */
    @Modifying
    @Query("DELETE FROM LabelSize ls WHERE ls.companyId = :companyId AND ls.isSystem = false")
    void deleteAllByCompanyId(@Param("companyId") Long companyId);
}
