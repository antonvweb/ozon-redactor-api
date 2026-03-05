package org.ozonLabel.ozonApi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ozon.*;
import org.ozonLabel.common.exception.ozon.FolderAccessDeniedException;
import org.ozonLabel.common.exception.ozon.FolderNotFoundException;
import org.ozonLabel.common.exception.ozon.InvalidFolderOperationException;
import org.ozonLabel.common.service.ozon.FolderService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.ozonApi.entity.OzonProduct;
import org.ozonLabel.ozonApi.entity.ProductFolder;
import org.ozonLabel.ozonApi.repository.DataMatrixCodeRepository;
import org.ozonLabel.ozonApi.repository.OzonProductRepository;
import org.ozonLabel.ozonApi.repository.ProductFolderRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ozonLabel.common.model.SourceType;
import org.ozonLabel.common.exception.user.ValidationException;
import org.ozonLabel.common.dto.label.LayerVisibilityRequest;
import org.ozonLabel.common.dto.label.LabelConfigDto;
import org.ozonLabel.common.dto.label.LayerDto;
import org.ozonLabel.ozonApi.entity.Label;
import org.ozonLabel.ozonApi.repository.LabelRepository;
import org.ozonLabel.ozonApi.service.OzonServiceIml;
import org.ozonLabel.common.dto.ozon.SyncProductsRequest;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderServiceIml implements FolderService {

    private final ProductFolderRepository folderRepository;
    private final OzonProductRepository productRepository;
    private final LabelRepository labelRepository;
    private final CompanyService companyService;
    private final ObjectMapper objectMapper;
    private final OzonServiceIml ozonService;
    private final DataMatrixCodeRepository datamatrixCodeRepository;

    @Transactional
    @CacheEvict(value = "folderTrees", key = "#companyOwnerId")
    public FolderResponseDto createFolder(String userEmail, Long companyOwnerId, CreateFolderDto dto) {
        companyService.checkAccess(userEmail, companyOwnerId);

        if (dto.getParentFolderId() != null) {
            validateFolderOwnership(companyOwnerId, dto.getParentFolderId());
        }

        if (folderRepository.findByUserIdAndNameAndParentFolderId(
                companyOwnerId, dto.getName(), dto.getParentFolderId()).isPresent()) {
            throw new InvalidFolderOperationException("Папка с таким именем уже существует.");
        }

        ProductFolder folder = ProductFolder.builder()
                .userId(companyOwnerId)
                .parentFolderId(dto.getParentFolderId())
                .name(dto.getName())
                .color(dto.getColor())
                .icon(dto.getIcon())
                .position(0)
                .sourceType(dto.getSourceType() != null ? dto.getSourceType() : SourceType.MANUAL)
                .isTemplate(dto.getIsTemplate() != null ? dto.getIsTemplate() : false)
                .build();

        folder = folderRepository.save(folder);
        log.info("Created folder '{}' for user {}", folder.getName(), userEmail);

        return mapToDto(folder);
    }

    @Transactional
    @CacheEvict(value = "folderTrees", key = "#companyOwnerId")
    public FolderResponseDto updateFolder(String userEmail, Long companyOwnerId, Long folderId, UpdateFolderDto dto) {
        // Проверяем доступ к компании (минимум MODERATOR для редактирования)
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = getFolderWithAccessCheck(folderId, companyOwnerId);

        if (dto.getParentFolderId() != null) {
            validateFolderMove(folderId, dto.getParentFolderId());
        }

        if (dto.getName() != null) folder.setName(dto.getName());
        if (dto.getParentFolderId() != null) folder.setParentFolderId(dto.getParentFolderId());
        if (dto.getColor() != null) folder.setColor(dto.getColor());
        if (dto.getIcon() != null) folder.setIcon(dto.getIcon());
        if (dto.getPosition() != null) folder.setPosition(dto.getPosition());
        if (dto.getIsTemplate() != null) folder.setIsTemplate(dto.getIsTemplate());

        folder = folderRepository.save(folder);
        log.info("Updated folder {} for user {}", folderId, userEmail);

        return mapToDto(folder);
    }

    @Transactional
    @CacheEvict(value = "folderTrees", key = "#companyOwnerId")
    public void deleteFolder(String userEmail, Long companyOwnerId, Long folderId, boolean moveProductsToParent) {
        // Проверяем доступ к компании (минимум MODERATOR для удаления)
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = getFolderWithAccessCheck(folderId, companyOwnerId);

        if (moveProductsToParent) {
            moveItemsToParent(companyOwnerId, folderId, folder.getParentFolderId());
        } else {
            clearFolderAndSubfolders(companyOwnerId, folderId);
        }

        folderRepository.deleteById(folderId);
        log.info("Deleted folder {} for user {}", folderId, userEmail);
    }

    @Cacheable(value = "folderTrees", key = "#companyOwnerId")
    public List<FolderTreeDto> getFolderTree(String userEmail, Long companyOwnerId) {
        log.info("getFolderTree called: userEmail={}, companyOwnerId={}", userEmail, companyOwnerId);
        companyService.checkAccess(userEmail, companyOwnerId);

        List<ProductFolder> rootFolders = folderRepository
                .findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(companyOwnerId);

        return rootFolders.stream()
                .map(folder -> buildFolderTree(folder, companyOwnerId))
                .collect(Collectors.toList());
    }

    public List<FolderResponseDto> getFolders(String userEmail, Long companyOwnerId, Long parentFolderId) {
        // Проверяем доступ к компании
        companyService.checkAccess(userEmail, companyOwnerId);

        List<ProductFolder> folders = parentFolderId == null
                ? folderRepository.findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(companyOwnerId)
                : folderRepository.findByUserIdAndParentFolderIdOrderByPositionAsc(companyOwnerId, parentFolderId);

        return folders.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public FolderResponseDto getFolder(String userEmail, Long companyOwnerId, Long folderId) {
        // Проверяем доступ к компании
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = getFolderWithAccessCheck(folderId, companyOwnerId);
        return mapToDto(folder);
    }

    @Transactional
    public void moveProductsToFolder(String userEmail, Long companyOwnerId, MoveProductsToFolderDto dto) {
        // Проверяем доступ к компании (минимум MODERATOR для перемещения)
        companyService.checkAccess(userEmail, companyOwnerId);

        if (dto.getTargetFolderId() != null) {
            validateFolderOwnership(companyOwnerId, dto.getTargetFolderId());
        }

        // Fetch all products in one query
        List<OzonProduct> products = productRepository.findAllById(dto.getProductIds());

        // Validate all products belong to company
        boolean allValid = products.stream()
                .allMatch(p -> p.getUserId().equals(companyOwnerId));

        if (!allValid) {
            throw new FolderAccessDeniedException("Некоторые товары не принадлежат этой компании.");
        }

        // Валидация sourceType при перемещении
        validateSourceTypeMove(products, dto.getTargetFolderId());

        // Use bulk update instead of saveAll
        int updated = productRepository.bulkMoveProductsToFolder(
                dto.getProductIds(),
                companyOwnerId,
                dto.getTargetFolderId()
        );

        log.info("Moved {} products to folder {} for user {}",
                updated, dto.getTargetFolderId(), userEmail);
    }

    public List<FolderPathDto> getFolderPath(String userEmail, Long companyOwnerId, Long folderId) {
        // Проверяем доступ к компании
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = getFolderWithAccessCheck(folderId, companyOwnerId);

        List<FolderPathDto> path = new ArrayList<>();
        ProductFolder current = folder;
        int level = 0;

        while (current != null) {
            path.add(0, FolderPathDto.builder()
                    .id(current.getId())
                    .name(current.getName())
                    .level(level++)
                    .build());

            current = current.getParentFolderId() != null
                    ? folderRepository.findById(current.getParentFolderId()).orElse(null)
                    : null;
        }

        return path;
    }

    @Override
    public List<FolderResponseDto> findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(Long userId) {
        return folderRepository.findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(userId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<FolderResponseDto> findByUserIdAndParentFolderIdOrderByPositionAsc(Long userId, Long parentFolderId) {
        return folderRepository.findByUserIdAndParentFolderIdOrderByPositionAsc(userId, parentFolderId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<FolderResponseDto> findByUserIdOrderByPositionAsc(Long userId) {
        return folderRepository.findByUserIdOrderByPositionAsc(userId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public Optional<FolderResponseDto> findByUserIdAndNameAndParentFolderId(Long userId, String name, Long parentFolderId) {
        return folderRepository.findByUserIdAndNameAndParentFolderId(userId, name, parentFolderId)
                .map(this::mapToDto);
    }

    @Override
    public boolean existsByUserIdAndId(Long userId, Long folderId) {
        return folderRepository.existsByUserIdAndId(userId, folderId);
    }

    @Override
    public List<Object[]> getFolderTreeForUser(Long userId) {
        return folderRepository.getFolderTreeForUser(userId);
    }

    @Override
    public List<String> getFolderPath(Long folderId) {
        return folderRepository.getFolderPath(folderId);
    }

    @Override
    public Long countByUserIdAndParentFolderId(Long userId, Long parentFolderId) {
        return folderRepository.countByUserIdAndParentFolderId(userId, parentFolderId);
    }

    @Override
    public List<Long> getAllSubfolderIds(Long folderId) {
        return folderRepository.getAllSubfolderIds(folderId);
    }


    // Private helper methods

    private FolderTreeDto buildFolderTree(ProductFolder folder, Long userId) {
        List<ProductFolder> subfolders = folderRepository
                .findByUserIdAndParentFolderIdOrderByPositionAsc(userId, folder.getId());

        Long productsCount = productRepository.countByUserIdAndFolderId(userId, folder.getId());

        return FolderTreeDto.builder()
                .id(folder.getId())
                .name(folder.getName())
                .color(folder.getColor())
                .icon(folder.getIcon())
                .position(folder.getPosition())
                .productsCount(productsCount.intValue())
                .subfolders(subfolders.stream()
                        .map(sf -> buildFolderTree(sf, userId))
                        .collect(Collectors.toList()))
                .build();
    }

    private FolderResponseDto mapToDto(ProductFolder folder) {
        Long productsCount = productRepository.countByUserIdAndFolderId(
                folder.getUserId(), folder.getId());
        Long subfoldersCount = folderRepository.countByUserIdAndParentFolderId(
                folder.getUserId(), folder.getId());

        return FolderResponseDto.builder()
                .id(folder.getId())
                .userId(folder.getUserId())
                .parentFolderId(folder.getParentFolderId())
                .name(folder.getName())
                .color(folder.getColor())
                .icon(folder.getIcon())
                .position(folder.getPosition())
                .isTemplate(folder.getIsTemplate())
                .productsCount(productsCount.intValue())
                .subfoldersCount(subfoldersCount.intValue())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }

    private ProductFolder getFolderWithAccessCheck(Long folderId, Long userId) {
        ProductFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new FolderNotFoundException("Folder not found"));

        if (!folder.getUserId().equals(userId)) {
            throw new FolderAccessDeniedException("Доступ к этой папке запрещен.");
        }

        return folder;
    }

    private void validateFolderOwnership(Long userId, Long folderId) {
        if (!folderRepository.existsByUserIdAndId(userId, folderId)) {
            throw new FolderNotFoundException("Папка не найдена");
        }
    }

    private void validateFolderMove(Long folderId, Long targetParentId) {
        if (folderId.equals(targetParentId)) {
            throw new InvalidFolderOperationException("Не удаётся переместить папку саму в себя.");
        }

        if (isSubfolderOf(targetParentId, folderId)) {
            throw new InvalidFolderOperationException("Не удаётся переместить папку в её подпапку.");
        }
    }

    private boolean isSubfolderOf(Long potentialSubfolderId, Long parentId) {
        ProductFolder folder = folderRepository.findById(potentialSubfolderId).orElse(null);

        while (folder != null && folder.getParentFolderId() != null) {
            if (folder.getParentFolderId().equals(parentId)) {
                return true;
            }
            folder = folderRepository.findById(folder.getParentFolderId()).orElse(null);
        }

        return false;
    }

    private void moveItemsToParent(Long userId, Long folderId, Long parentFolderId) {
        // Move products
        List<OzonProduct> products = productRepository.findByUserIdAndFolderId(userId, folderId);
        products.forEach(p -> p.setFolderId(parentFolderId));
        productRepository.saveAll(products);

        // Move subfolders
        List<ProductFolder> subfolders = folderRepository
                .findByUserIdAndParentFolderIdOrderByPositionAsc(userId, folderId);
        subfolders.forEach(sf -> sf.setParentFolderId(parentFolderId));
        folderRepository.saveAll(subfolders);
    }

    private void clearFolderAndSubfolders(Long userId, Long folderId) {
        List<Long> allFolderIds = folderRepository.getAllSubfolderIds(folderId);
        allFolderIds.add(folderId);

        for (Long folId : allFolderIds) {
            List<OzonProduct> products = productRepository.findByUserIdAndFolderId(userId, folId);
            products.forEach(p -> p.setFolderId(null));
            productRepository.saveAll(products);
        }
    }

    /**
     * Валидация перемещения товаров по типу источника (sourceType)
     * - API → можно перемещать только в подпапки API-папок или в корень
     * - EXCEL → можно перемещать только в подпапки EXCEL-папок или в корень
     * - MANUAL → можно в любую папку
     * - TEMPLATE → можно в любую папку
     */
    private void validateSourceTypeMove(List<OzonProduct> products, Long targetFolderId) {
        if (targetFolderId == null) {
            // Перемещение в корень — всегда разрешено
            return;
        }

        // Определяем sourceType исходных папок товаров
        Set<Long> sourceFolderIds = products.stream()
                .map(OzonProduct::getFolderId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        if (sourceFolderIds.isEmpty()) {
            // Товары без папки — можно перемещать куда угодно
            return;
        }

        // Получаем sourceType исходных папок
        List<ProductFolder> sourceFolders = folderRepository.findAllById(sourceFolderIds);
        
        // Проверяем есть ли папки типа API или EXCEL
        boolean hasApiSource = sourceFolders.stream()
                .anyMatch(f -> f.getSourceType() == SourceType.API);
        boolean hasExcelSource = sourceFolders.stream()
                .anyMatch(f -> f.getSourceType() == SourceType.EXCEL);

        if (!hasApiSource && !hasExcelSource) {
            // Только MANUAL или TEMPLATE — можно перемещать куда угодно
            return;
        }

        // Проверяем целевую папку
        ProductFolder targetFolder = folderRepository.findById(targetFolderId)
                .orElseThrow(() -> new FolderNotFoundException("Целевая папка не найдена"));

        // Если товары из API-папки
        if (hasApiSource) {
            if (targetFolder.getSourceType() != SourceType.API && !targetFolder.isRootFolder()) {
                throw new ValidationException(
                    "Товары из API-папки можно перемещать только в подпапки API или в корень"
                );
            }
            // Проверяем всю иерархию родительских папок — все должны быть API или корень
            ProductFolder current = targetFolder;
            while (current != null && current.getParentFolderId() != null) {
                current = folderRepository.findById(current.getParentFolderId()).orElse(null);
                if (current != null && current.getSourceType() != SourceType.API) {
                    throw new ValidationException(
                        "Товары из API-папки можно перемещать только в подпапки API или в корень"
                    );
                }
            }
        }

        // Если товары из EXCEL-папки
        if (hasExcelSource) {
            if (targetFolder.getSourceType() != SourceType.EXCEL && !targetFolder.isRootFolder()) {
                throw new ValidationException(
                    "Товары из EXCEL-папки можно перемещать только в подпапки EXCEL или в корень"
                );
            }
            // Проверяем всю иерархию родительских папок — все должны быть EXCEL или корень
            ProductFolder current = targetFolder;
            while (current != null && current.getParentFolderId() != null) {
                current = folderRepository.findById(current.getParentFolderId()).orElse(null);
                if (current != null && current.getSourceType() != SourceType.EXCEL) {
                    throw new ValidationException(
                        "Товары из EXCEL-папки можно перемещать только в подпапки EXCEL или в корень"
                    );
                }
            }
        }
    }

    @Override
    @Transactional
    public void updateLayerVisibility(String userEmail, Long companyOwnerId, Long folderId, LayerVisibilityRequest dto) {
        companyService.checkAccess(userEmail, companyOwnerId);

        // Получаем все продукты в папке
        List<OzonProduct> products = productRepository.findByUserIdAndFolderId(companyOwnerId, folderId);
        
        if (products.isEmpty()) {
            log.info("В папке {} нет продуктов для обновления видимости слоя", folderId);
            return;
        }

        List<Long> productIds = products.stream()
                .map(OzonProduct::getProductId)
                .toList();

        // Получаем все этикетки для этих продуктов
        List<Label> labels = labelRepository.findByCompanyIdAndProductIdIn(companyOwnerId, productIds);
        
        if (labels.isEmpty()) {
            log.info("Для продуктов папки {} не найдено этикеток", folderId);
            return;
        }

        // Обновляем видимость слоя в каждой этикетке
        int updatedCount = 0;
        for (Label label : labels) {
            try {
                LabelConfigDto config = objectMapper.readValue(label.getConfig(), LabelConfigDto.class);
                
                // Находим и обновляем слой
                boolean layerFound = false;
                for (LayerDto layer : config.getLayers()) {
                    if (layer.getId().equals(dto.getLayerId())) {
                        layer.setVisible(dto.getVisible());
                        layerFound = true;
                        break;
                    }
                }

                if (layerFound) {
                    // Сериализуем обратно в JSON
                    label.setConfig(objectMapper.writeValueAsString(config));
                    updatedCount++;
                }
            } catch (Exception e) {
                log.error("Ошибка обновления конфигурации этикетки id={}: {}", label.getId(), e.getMessage());
            }
        }

        // Сохраняем все изменения batch update
        if (updatedCount > 0) {
            labelRepository.saveAll(labels);
            log.info("Обновлена видимость слоя {} для {} этикеток в папке {} пользователем {}", 
                    dto.getLayerId(), updatedCount, folderId, userEmail);
        }
    }

    @Override
    @Transactional
    public void refreshFolder(String userEmail, Long companyOwnerId, Long folderId) {
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new FolderNotFoundException("Папка не найдена"));

        if (!folder.getUserId().equals(companyOwnerId)) {
            throw new FolderAccessDeniedException("Доступ запрещён");
        }

        // Проверяем sourceType
        switch (folder.getSourceType()) {
            case API:
                // Пересинхронизация товаров из Ozon API
                List<OzonProduct> products = productRepository.findByUserIdAndFolderId(companyOwnerId, folderId);
                List<Long> productIds = products.stream()
                        .map(OzonProduct::getProductId)
                        .toList();

                if (!productIds.isEmpty()) {
                    // Создаём запрос на синхронизацию только для этих продуктов
                    SyncProductsRequest request = SyncProductsRequest.builder()
                            .productIds(productIds)
                            .build();
                    
                    // Вызываем синхронизацию
                    ozonService.syncProducts(companyOwnerId, request, folderId);
                    log.info("Выполнена пересинхронизация папки {} для компании {} пользователем {}", 
                            folderId, companyOwnerId, userEmail);
                }
                break;

            case EXCEL:
                throw new ValidationException(
                    "Для папок типа EXCEL необходимо перезагрузить файл"
                );

            case MANUAL:
            case TEMPLATE:
                throw new ValidationException(
                    "Папки этого типа не поддерживают обновление"
                );

            default:
                throw new ValidationException(
                    "Неподдерживаемый тип папки: " + folder.getSourceType()
                );
        }
    }

    @Override
    @Transactional
    public FolderResponseDto toggleTemplate(String userEmail, Long companyOwnerId, Long folderId, Boolean isTemplate) {
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = getFolderWithAccessCheck(folderId, companyOwnerId);
        folder.setIsTemplate(isTemplate);
        folder = folderRepository.save(folder);

        log.info("Папка {} установлена как шаблонная: {} пользователем {}", folderId, isTemplate, userEmail);
        return mapToDto(folder);
    }

    @Override
    public FolderDataMatrixStats getFolderDataMatrixStats(String userEmail, Long companyOwnerId, Long folderId) {
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = getFolderWithAccessCheck(folderId, companyOwnerId);

        // Получаем все продукты в папке
        List<OzonProduct> products = productRepository.findByUserIdAndFolderId(companyOwnerId, folderId);
        List<Long> productIds = products.stream()
                .map(OzonProduct::getProductId)
                .toList();

        if (productIds.isEmpty()) {
            return FolderDataMatrixStats.builder()
                    .totalCodes(0L)
                    .remainingCodes(0L)
                    .productsWithCodes(0)
                    .productsWithoutCodes(0)
                    .build();
        }

        // Получаем статистику по DataMatrix кодам
        List<Object[]> stats = datamatrixCodeRepository.getStatsByProductIds(companyOwnerId, productIds);

        long totalCodes = 0;
        long remainingCodes = 0;
        Set<Long> productsWithCodesSet = stats.stream()
                .map(row -> (Long) row[0])
                .collect(Collectors.toSet());

        for (Object[] row : stats) {
            totalCodes += (Long) row[1];
            remainingCodes += (Long) row[2];
        }

        int productsWithCodes = productsWithCodesSet.size();
        int productsWithoutCodes = productIds.size() - productsWithCodes;

        return FolderDataMatrixStats.builder()
                .totalCodes(totalCodes)
                .remainingCodes(remainingCodes)
                .productsWithCodes(productsWithCodes)
                .productsWithoutCodes(productsWithoutCodes)
                .build();
    }

    @Override
    @Transactional
    public OrderUploadResult uploadPrintOrder(String userEmail, Long companyOwnerId, MultipartFile file) {
        companyService.checkAccess(userEmail, companyOwnerId);

        List<String> notFoundBarcodes = new ArrayList<>();
        List<AmbiguousBarcodeDto> ambiguous = new ArrayList<>();
        List<OzonProduct> productsToUpdate = new ArrayList<>();
        int matchedCount = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Находим индексы колонок по заголовкам
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new ValidationException("Excel файл не содержит заголовков");
            }

            int barcodeColIdx = -1;
            int quantityColIdx = -1;

            for (Cell cell : headerRow) {
                String cellValue = getCellValueAsString(cell).trim();
                if ("Штрихкод".equalsIgnoreCase(cellValue) || "Barcode".equalsIgnoreCase(cellValue)) {
                    barcodeColIdx = cell.getColumnIndex();
                } else if ("Количество".equalsIgnoreCase(cellValue) || "Quantity".equalsIgnoreCase(cellValue)
                        || "Кол-во".equalsIgnoreCase(cellValue)) {
                    quantityColIdx = cell.getColumnIndex();
                }
            }

            if (barcodeColIdx == -1 || quantityColIdx == -1) {
                throw new ValidationException("Excel файл должен содержать колонки 'Штрихкод' и 'Количество'");
            }

            // Обрабатываем строки данных
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String barcode = getCellValueAsString(row.getCell(barcodeColIdx)).trim();
                if (barcode.isEmpty()) continue;

                String quantityStr = getCellValueAsString(row.getCell(quantityColIdx)).trim();
                int quantity;
                try {
                    quantity = Integer.parseInt(quantityStr);
                } catch (NumberFormatException e) {
                    log.warn("Некорректное количество для штрихкода {}: {}", barcode, quantityStr);
                    continue;
                }

                // Ищем товары по штрихкоду
                List<OzonProduct> products = productRepository.findByUserIdAndBarcode(companyOwnerId, barcode);

                if (products.isEmpty()) {
                    notFoundBarcodes.add(barcode);
                } else if (products.size() == 1) {
                    products.get(0).setPrintQuantity(quantity);
                    productsToUpdate.add(products.get(0));
                    matchedCount++;
                } else {
                    // Несколько товаров с одинаковым штрихкодом в разных папках
                    List<FolderInfoDto> folders = products.stream()
                            .map(p -> FolderInfoDto.builder()
                                    .folderId(p.getFolderId())
                                    .folderName(getFolderName(p.getFolderId()))
                                    .build())
                            .distinct()
                            .toList();

                    ambiguous.add(AmbiguousBarcodeDto.builder()
                            .barcode(barcode)
                            .quantity(quantity)
                            .folders(folders)
                            .build());
                }
            }

            // Batch-сохранение обновлённых товаров
            if (!productsToUpdate.isEmpty()) {
                productRepository.saveAll(productsToUpdate);
                log.info("Обновлено {} товаров для печати пользователем {}", productsToUpdate.size(), userEmail);
            }

        } catch (Exception e) {
            log.error("Ошибка при загрузке Excel файла: {}", e.getMessage(), e);
            throw new ValidationException("Ошибка при чтении Excel файла: " + e.getMessage());
        }

        return OrderUploadResult.builder()
                .matchedCount(matchedCount)
                .notFoundCount(notFoundBarcodes.size())
                .notFoundBarcodes(notFoundBarcodes)
                .ambiguous(ambiguous)
                .build();
    }

    @Override
    @Transactional
    public void resolvePrintOrder(String userEmail, Long companyOwnerId, ResolveOrderRequest request) {
        companyService.checkAccess(userEmail, companyOwnerId);

        List<OzonProduct> productsToUpdate = new ArrayList<>();

        for (ResolveOrderRequest.BarcodeResolution resolution : request.getResolutions()) {
            // Находим товар в указанной папке по штрихкоду
            List<OzonProduct> products = productRepository.findByUserIdAndFolderId(companyOwnerId, resolution.getFolderId())
                    .stream()
                    .filter(p -> p.getBarcodes() != null && p.getBarcodes().contains(resolution.getBarcode()))
                    .toList();

            if (!products.isEmpty()) {
                products.get(0).setPrintQuantity(resolution.getQuantity());
                productsToUpdate.add(products.get(0));
            }
        }

        if (!productsToUpdate.isEmpty()) {
            productRepository.saveAll(productsToUpdate);
            log.info("Разрешено {} неоднозначностей штрихкодов пользователем {}", productsToUpdate.size(), userEmail);
        }
    }

    @Override
    public List<FolderResponseDto> getFoldersBySourceType(String userEmail, Long companyOwnerId, String sourceTypeStr) {
        companyService.checkAccess(userEmail, companyOwnerId);

        SourceType sourceType;
        try {
            sourceType = SourceType.valueOf(sourceTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Некорректный тип источника: " + sourceTypeStr);
        }

        List<ProductFolder> folders = folderRepository.findByUserIdAndSourceType(companyOwnerId, sourceType);
        return folders.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // Вспомогательные методы

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        yield String.valueOf((long) numericValue);
                    } else {
                        yield String.valueOf(numericValue);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String getFolderName(Long folderId) {
        if (folderId == null) return "Без папки";
        return folderRepository.findById(folderId)
                .map(ProductFolder::getName)
                .orElse("Неизвестно");
    }
}