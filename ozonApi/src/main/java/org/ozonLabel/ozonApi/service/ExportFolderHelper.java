package org.ozonLabel.ozonApi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.ozonApi.entity.OzonProduct;
import org.ozonLabel.ozonApi.entity.ProductFolder;
import org.ozonLabel.ozonApi.repository.OzonProductRepository;
import org.ozonLabel.ozonApi.repository.ProductFolderRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Вспомогательный сервис для работы с папками при экспорте
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportFolderHelper {

    private final OzonProductRepository productRepository;
    private final ProductFolderRepository folderRepository;
    private final ObjectMapper objectMapper;

    /**
     * Собрать все productId из папок (с учётом includeSubfolders).
     * Если includeSubfolders=true — рекурсивно обходит подпапки.
     *
     * @param companyOwnerId ID компании (userId)
     * @param folderIds список ID папок
     * @param includeSubfolders включать ли подпапки
     * @return список ID продуктов
     */
    public List<Long> collectProductIds(Long companyOwnerId, List<Long> folderIds, boolean includeSubfolders) {
        Set<Long> allProductIds = folderIds.stream()
                .flatMap(folderId -> collectProductIdsFromFolder(companyOwnerId, folderId, includeSubfolders).stream())
                .collect(Collectors.toSet());

        return new ArrayList<>(allProductIds);
    }

    /**
     * Собрать productId из одной папки (рекурсивно если нужно).
     */
    private List<Long> collectProductIdsFromFolder(Long companyOwnerId, Long folderId, boolean includeSubfolders) {
        List<Long> result = new ArrayList<>();

        // Продукты из текущей папки
        List<OzonProduct> productsInFolder = productRepository.findByUserIdAndFolderId(companyOwnerId, folderId);
        result.addAll(productsInFolder.stream()
                .map(OzonProduct::getProductId)
                .toList());

        // Если нужно включить подпапки — рекурсивно обходим их
        if (includeSubfolders) {
            List<Long> subfolderIds = folderRepository.getAllSubfolderIds(folderId);
            for (Long subfolderId : subfolderIds) {
                List<OzonProduct> productsInSubfolder = productRepository.findByUserIdAndFolderId(companyOwnerId, subfolderId);
                result.addAll(productsInSubfolder.stream()
                        .map(OzonProduct::getProductId)
                        .toList());
            }
        }

        return result;
    }

    /**
     * Получить имя файла для продукта по стратегии.
     * fileNaming="barcode" → первый штрихкод из barcodes JSON
     * fileNaming="article" → offerId
     * Fallback: productId.toString()
     *
     * @param product
     * @param fileNaming стратегия именования
     * @return имя файла (без расширения)
     */
    public String getFileName(OzonProduct product, String fileNaming) {
        if ("barcode".equals(fileNaming)) {
            String barcode = getFirstBarcode(product);
            if (barcode != null && !barcode.isBlank()) {
                return sanitizeFileName(barcode);
            }
        }

        if ("article".equals(fileNaming)) {
            if (product.getOfferId() != null && !product.getOfferId().isBlank()) {
                return sanitizeFileName(product.getOfferId());
            }
        }

        // Fallback
        return product.getProductId().toString();
    }

    /**
     * Получить первый штрихкод из JSONB поля barcodes.
     */
    private String getFirstBarcode(OzonProduct product) {
        if (product.getBarcodes() == null || product.getBarcodes().isBlank()) {
            return null;
        }

        try {
            List<String> barcodes = objectMapper.readValue(
                    product.getBarcodes(),
                    new TypeReference<List<String>>() {}
            );
            if (barcodes != null && !barcodes.isEmpty()) {
                return barcodes.get(0);
            }
        } catch (Exception e) {
            log.warn("Ошибка парсинга barcodes для продукта {}: {}", product.getProductId(), e.getMessage());
        }

        return null;
    }

    /**
     * Очистить имя файла от недопустимых символов.
     */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[<>:\"/\\\\|？*]", "_").trim();
    }
}
