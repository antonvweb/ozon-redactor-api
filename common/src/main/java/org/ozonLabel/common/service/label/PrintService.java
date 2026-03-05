package org.ozonLabel.common.service.label;

import org.ozonLabel.common.dto.label.PickListRequest;
import org.ozonLabel.common.dto.label.PrintRequest;
import org.ozonLabel.common.dto.label.PrintResponse;

/**
 * Сервис для генерации PDF для печати
 */
public interface PrintService {

    /**
     * Сгенерировать PDF с этикетками
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param request параметры печати
     * @return ответ с PDF и метриками
     */
    PrintResponse generateLabelsPdf(String userEmail, Long companyOwnerId, PrintRequest request);
    
    /**
     * Сгенерировать PDF листа подбора
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param request параметры печати
     * @return PDF файл в виде массива байтов
     */
    byte[] generatePickListPdf(String userEmail, Long companyOwnerId, PickListRequest request);
}
