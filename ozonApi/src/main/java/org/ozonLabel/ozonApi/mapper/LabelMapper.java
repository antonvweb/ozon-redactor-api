package org.ozonLabel.ozonApi.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.label.LabelConfigDto;
import org.ozonLabel.common.dto.label.LabelResponseDto;
import org.ozonLabel.ozonApi.entity.Label;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LabelMapper {

    private final ObjectMapper objectMapper;

    public LabelResponseDto toDto(Label label) {
        LabelConfigDto config = parseConfig(label.getConfig());

        return LabelResponseDto.builder()
                .id(label.getId())
                .userId(label.getUserId())
                .companyId(label.getCompanyId())
                .productId(label.getProductId())
                .name(label.getName())
                .width(label.getWidth())
                .height(label.getHeight())
                .unit(label.getUnit())
                .config(config)
                .createdAt(label.getCreatedAt())
                .updatedAt(label.getUpdatedAt())
                .build();
    }

    private LabelConfigDto parseConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(configJson, LabelConfigDto.class);
        } catch (JsonProcessingException e) {
            log.error("Ошибка парсинга конфигурации этикетки", e);
            return null;
        }
    }
}
