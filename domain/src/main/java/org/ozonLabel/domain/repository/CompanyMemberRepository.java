package org.ozonLabel.domain.repository;
import org.ozonLabel.domain.model.CompanyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Long> {

    // Найти все компании (владельцев), к которым привязан пользователь
    List<CompanyMember> findByMemberUserId(Long memberUserId);

    // Найти всех членов команды конкретной компании
    List<CompanyMember> findByCompanyOwnerId(Long companyOwnerId);

    // Найти конкретную связь
    Optional<CompanyMember> findByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberUserId);

    // Проверить, является ли пользователь членом компании
    boolean existsByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberUserId);

    // Получить роль пользователя в компании
    @Query("SELECT cm.role FROM CompanyMember cm WHERE cm.companyOwnerId = :ownerId AND cm.memberUserId = :memberId")
    Optional<CompanyMember.MemberRole> findRoleByCompanyOwnerIdAndMemberUserId(
            @Param("ownerId") Long ownerId,
            @Param("memberId") Long memberId
    );

    // Удалить члена из компании
    void deleteByCompanyOwnerIdAndMemberUserId(Long companyOwnerId, Long memberUserId);

    // Подсчитать количество членов в компании
    Long countByCompanyOwnerId(Long companyOwnerId);
}