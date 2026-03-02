package org.ozonLabel.ozonApi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.datamatrix.DataMatrixCodeDto;
import org.ozonLabel.common.dto.datamatrix.DataMatrixStatsDto;
import org.ozonLabel.common.dto.datamatrix.DataMatrixUploadResponse;
import org.ozonLabel.common.service.datamatrix.DataMatrixService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/datamatrix")
@RequiredArgsConstructor
@Slf4j
public class DataMatrixController {

    private final DataMatrixService dataMatrixService;

    /**
     * Загрузить коды DataMatrix из файла (PDF или CSV)
     */
    @PostMapping("/upload")
    public ResponseEntity<DataMatrixUploadResponse> uploadCodes(
            @RequestParam Long companyOwnerId,
            @RequestParam Long productId,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "true") boolean checkDuplicates,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Загрузка кодов DataMatrix для продукта {} компании {} пользователем {}",
                productId, companyOwnerId, userEmail);

        DataMatrixUploadResponse response = dataMatrixService.uploadCodes(
                userEmail, companyOwnerId, productId, file, checkDuplicates);
        return ResponseEntity.ok(response);
    }

    /**
     * Получить коды для продукта с пагинацией
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<DataMatrixCodeDto>> getCodesForProduct(
            @PathVariable Long productId,
            @RequestParam Long companyOwnerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Получение кодов DataMatrix для продукта {} компании {} пользователем {}",
                productId, companyOwnerId, userEmail);

        Page<DataMatrixCodeDto> codes = dataMatrixService.getCodesForProduct(
                userEmail, companyOwnerId, productId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(codes);
    }

    /**
     * Получить статистику по кодам для продукта
     */
    @GetMapping("/product/{productId}/stats")
    public ResponseEntity<DataMatrixStatsDto> getStatsForProduct(
            @PathVariable Long productId,
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Получение статистики DataMatrix для продукта {} компании {} пользователем {}",
                productId, companyOwnerId, userEmail);

        DataMatrixStatsDto stats = dataMatrixService.getStatsForProduct(
                userEmail, companyOwnerId, productId);
        return ResponseEntity.ok(stats);
    }

    /**
     * Удалить код по ID
     */
    @DeleteMapping("/{codeId}")
    public ResponseEntity<Void> deleteCode(
            @PathVariable Long codeId,
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Удаление кода DataMatrix {} компании {} пользователем {}",
                codeId, companyOwnerId, userEmail);

        dataMatrixService.deleteCode(userEmail, companyOwnerId, codeId);
        return ResponseEntity.ok().build();
    }
}
