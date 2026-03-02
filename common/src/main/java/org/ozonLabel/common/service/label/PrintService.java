package org.ozonLabel.common.service.label;

import org.ozonLabel.common.dto.label.PickListRequest;
import org.ozonLabel.common.dto.label.PrintRequest;

/**
 * Сервис для генерации PDF для печати
 */
public interface PrintService {
    
    /**
     * Сгенерировать PDF с этикетками
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param request параметры печати
     * @return PDF файл в виде массива байтов
     */
    byte[] generateLabelsPdf(String userEmail, Long companyOwnerId, PrintRequest request);
    
    /**
     * Сгенерировать PDF листа подбора
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param request параметры печати
     * @return PDF файл в виде массива байтов
     */
    byte[] generatePickListPdf(String userEmail, Long companyOwnerId, PickListRequest request);
}
