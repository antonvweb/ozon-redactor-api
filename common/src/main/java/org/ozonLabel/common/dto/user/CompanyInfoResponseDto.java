package org.ozonLabel.common.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyInfoResponseDto {

    // Информация о владельце компании
    private Long ownerId;
    private String ownerName;
    private String ownerEmail;
    private String companyName;
    private String inn;
    private String phone;
    private String ozonClientId;
    private String subscription;

    // Роль текущего пользователя в этой компании
    private String myRole;

    // Список всех членов команды (только для ADMIN)
    private List<TeamMember> teamMembers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMember {
        private Long userId;
        private String userName;
        private String userEmail;
        private String role;
    }
}