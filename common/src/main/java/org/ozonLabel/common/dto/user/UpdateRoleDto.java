package org.ozonLabel.common.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ozonLabel.common.model.MemberRole;

// DTO для изменения роли
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleDto {
    private MemberRole role;
}