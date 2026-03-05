package org.ozonLabel.ozonApi.repository;

import org.ozonLabel.ozonApi.entity.TableColumnSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TableColumnSettingsRepository extends JpaRepository<TableColumnSettings, Long> {
    
    Optional<TableColumnSettings> findByCompanyId(Long companyId);
    
    boolean existsByCompanyId(Long companyId);
    
    @Modifying
    @Query("UPDATE TableColumnSettings t SET t.columns = :columns WHERE t.companyId = :companyId")
    int updateColumnsByCompanyId(@Param("companyId") Long companyId, @Param("columns") String columns);
}
