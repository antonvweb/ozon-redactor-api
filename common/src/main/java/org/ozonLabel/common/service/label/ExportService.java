package org.ozonLabel.common.service.label;

import org.ozonLabel.common.dto.label.ExportRequest;

/**
 * Сервис для экспорта этикеток
 */
public interface ExportService {
    
    /**
     * Экспортировать этикетки в Excel или PDF
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param request параметры экспорта
     * @return файл в виде массива байтов
     */
    byte[] exportLabels(String userEmail, Long companyOwnerId, ExportRequest request);
}
