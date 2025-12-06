package org.ozonLabel.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ozonLabel.domain.model.CompanyMember;

// DTO для изменения роли
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleDto {
    private CompanyMember.MemberRole role;
}