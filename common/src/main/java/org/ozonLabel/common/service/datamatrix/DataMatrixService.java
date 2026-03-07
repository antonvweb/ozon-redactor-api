package org.ozonLabel.common.service.datamatrix;

import org.ozonLabel.common.dto.datamatrix.DataMatrixCodeDto;
import org.ozonLabel.common.dto.datamatrix.DataMatrixFileDto;
import org.ozonLabel.common.dto.datamatrix.DataMatrixStatsDto;
import org.ozonLabel.common.dto.datamatrix.DataMatrixUploadResponse;
import org.ozonLabel.common.dto.datamatrix.DeleteFileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * Сервис для управления кодами DataMatrix (Честный знак)
 */
public interface DataMatrixService {
    
    /**
     * Загрузить коды DataMatrix из файла (PDF или CSV)
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param productId ID продукта
     * @param file файл с кодами
     * @param checkDuplicates проверять на дубликаты
     * @return результат загрузки
     */
    DataMatrixUploadResponse uploadCodes(
        String userEmail,
        Long companyOwnerId,
        Long productId,
        MultipartFile file,
        boolean checkDuplicates
    );
    
    /**
     * Получить коды для продукта с пагинацией
     */
    Page<DataMatrixCodeDto> getCodesForProduct(
        String userEmail,
        Long companyOwnerId,
        Long productId,
        Pageable pageable
    );
    
    /**
     * Получить статистику по кодам для продукта
     */
    DataMatrixStatsDto getStatsForProduct(
        String userEmail,
        Long companyOwnerId,
        Long productId
    );
    
    /**
     * Удалить код по ID
     */
    void deleteCode(String userEmail, Long companyOwnerId, Long codeId);
    
    /**
     * Зарезервировать следующий неиспользованный код для продукта
     * @return код или null если кодов нет
     */
    Optional<String> reserveNextCodeForProduct(
        String userEmail,
        Long companyOwnerId,
        Long productId
    );

    /**
     * Зарезервировать следующий неиспользованный код из конкретного файла
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param fileId ID файла с кодами
     * @return код или пустой Optional если кодов нет
     */
    Optional<String> reserveNextCodeFromFile(
        String userEmail,
        Long companyOwnerId,
        Long fileId
    );

    /**
     * Получить статистику по кодам для конкретного файла
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param fileId ID файла
     * @return статистика по файлу
     */
    DataMatrixStatsDto getStatsForFile(
        String userEmail,
        Long companyOwnerId,
        Long fileId
    );
    
    /**
     * Распарсить GS1 DataMatrix код
     * @param rawCode сырой код
     * @return распарсенные данные {gtin, serial, verificationKey}
     */
    GS1DataMatrixResult parseGS1Code(String rawCode);

    /**
     * Получить историю файлов для продукта
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param productId ID продукта
     * @return список файлов с кодами
     */
    java.util.List<DataMatrixFileDto> getFileHistory(
        String userEmail,
        Long companyOwnerId,
        Long productId
    );

    /**
     * Скачать оригинальный CSV файл
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param fileId ID файла
     * @return содержимое файла как byte[]
     */
    byte[] downloadFileAsCSV(
        String userEmail,
        Long companyOwnerId,
        Long fileId
    );

    /**
     * Скачать список дубликатов как CSV
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param fileId ID файла
     * @return содержимое файла с дубликатами как byte[]
     */
    byte[] downloadDuplicatesAsCSV(
        String userEmail,
        Long companyOwnerId,
        Long fileId
    );

    /**
     * Удалить файл с кодами
     * @param userEmail email пользователя
     * @param companyOwnerId ID компании
     * @param fileId ID файла
     * @return результат удаления
     */
    DeleteFileResponse deleteFile(
        String userEmail,
        Long companyOwnerId,
        Long fileId
    );

    /**
     * Результат парсинга GS1 кода
     */
    record GS1DataMatrixResult(String gtin, String serial, String verificationKey) {}
}
