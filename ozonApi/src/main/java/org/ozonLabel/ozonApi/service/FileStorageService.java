package org.ozonLabel.ozonApi.service;

import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.exception.user.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:http://localhost:8082/uploads}")
    private String baseUrl;

    public String store(MultipartFile file, String storedName, Long companyId) {
        try {
            Path uploadPath = Paths.get(uploadDir, "company_" + companyId, "images");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(storedName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = "company_" + companyId + "/images/" + storedName;
            log.info("Файл сохранен: {}", relativePath);
            return relativePath;
        } catch (IOException e) {
            log.error("Ошибка сохранения файла: {}", storedName, e);
            throw new ValidationException("Не удалось сохранить файл");
        }
    }

    public String getPublicUrl(String storagePath) {
        return baseUrl + "/" + storagePath;
    }

    public void delete(String storagePath) {
        try {
            Path filePath = Paths.get(uploadDir, storagePath);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Файл удален: {}", storagePath);
            }
        } catch (IOException e) {
            log.error("Ошибка удаления файла: {}", storagePath, e);
        }
    }
}
