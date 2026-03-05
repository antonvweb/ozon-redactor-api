package org.ozonLabel.common.service.ozon;

import org.ozonLabel.common.dto.ozon.CreateProductBySizeDto;
import org.ozonLabel.common.dto.ozon.ExcelImportResult;
import org.ozonLabel.common.dto.ozon.ProductInfo;
import org.springframework.web.multipart.MultipartFile;

public interface ProductCreationService {
    /**
     * Создать товар по размеру
     *
     * @param userEmail email пользователя
     * @param dto DTO с информацией о товаре и размере
     * @return созданный OzonProduct
     */
    ProductInfo createProductBySize(String userEmail, CreateProductBySizeDto dto);

    /**
     * Импортировать товары из Excel файла
     *
     * @param userEmail email пользователя
     * @param companyOwnerId ID владельца компании
     * @param file Excel файл с данными
     * @param folderId id папки (может быть null)
     * @return результат импорта
     */
    ExcelImportResult importFromExcel(String userEmail, Long companyOwnerId, MultipartFile file, Long folderId);

    /**
     * Обновить данные папки из Excel файла (обновление файла)
     *
     * @param userEmail email пользователя
     * @param companyOwnerId ID владельца компании
     * @param folderId ID папки для обновления
     * @param file Excel файл с новыми данными
     * @return результат обновления
     */
    ExcelImportResult updateExcelFile(String userEmail, Long companyOwnerId, Long folderId, MultipartFile file);
}
