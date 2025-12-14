package org.ozonLabel.common.service.ozon;

import org.ozonLabel.common.dto.ozon.CreateProductBySizeDto;
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
     * Создать товар из Excel файла
     *
     * @param userEmail email пользователя
     * @param file Excel файл с данными
     * @param folderId id папки (может быть null)
     * @return созданный OzonProduct
     */
    ProductInfo createProductFromExcel(String userEmail, MultipartFile file, Long folderId);
}
