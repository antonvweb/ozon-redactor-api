package org.ozonLabel.common.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ozonLabel.common.model.MemberRole;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyMemberDto {

    private Long id;                    // ID связи
    private Long companyOwnerId;        // ID владельца компании
    private Long memberUserId;          // ID участника
    private MemberRole role;            // Роль участника
    private LocalDateTime createdAt;    // Дата создания связи
    private LocalDateTime updatedAt;    // Дата обновления

    // Дополнительно можно добавить имена/почту участников
    private String companyOwnerName;
    private String companyOwnerEmail;
    private String memberUserName;
    private String memberUserEmail;
}
