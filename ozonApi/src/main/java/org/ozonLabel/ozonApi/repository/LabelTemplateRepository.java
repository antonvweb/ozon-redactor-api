package org.ozonLabel.ozonApi.repository;

import org.ozonLabel.ozonApi.entity.LabelTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabelTemplateRepository extends JpaRepository<LabelTemplate, Long> {
    
    /**
     * Найти все шаблоны для компании (системные + пользовательские)
     */
    @Query("SELECT t FROM LabelTemplate t WHERE t.isSystem = true OR t.companyId = :companyId")
    List<LabelTemplate> findByCompanyIdOrSystem(@Param("companyId") Long companyId);
    
    /**
     * Найти все системные шаблоны
     */
    List<LabelTemplate> findByIsSystemTrue();
    
    /**
     * Найти все пользовательские шаблоны компании
     */
    List<LabelTemplate> findByCompanyId(Long companyId);
    
    /**
     * Проверить является ли шаблон системным
     */
    boolean existsByIdAndIsSystemTrue(Long id);
    
    /**
     * Найти шаблон по ID и компании (или системный)
     */
    @Query("SELECT t FROM LabelTemplate t WHERE t.id = :id AND (t.companyId = :companyId OR t.isSystem = true)")
    LabelTemplate findByIdAndCompanyIdOrSystem(@Param("id") Long id, @Param("companyId") Long companyId);
}
