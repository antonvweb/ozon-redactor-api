package org.ozonLabel.common.dto.ozon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Краткая информация о папке для отображения в диалоге выбора
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderInfoDto {
    /**
     * ID папки
     */
    private Long folderId;

    /**
     * Название папки
     */
    private String folderName;
}
