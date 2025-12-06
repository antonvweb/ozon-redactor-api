package org.ozonLabel.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCompaniesResponseDto {

    private List<CompanyInfo> companies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyInfo {
        private Long companyOwnerId;
        private String companyName;
        private String companyEmail;
        private String companyInn;
        private String companyPhone;
        private String myRole;                    // "ADMIN", "MODERATOR", "VIEWER"
        private String subscription;              // "free", "pro", etc.
    }
}