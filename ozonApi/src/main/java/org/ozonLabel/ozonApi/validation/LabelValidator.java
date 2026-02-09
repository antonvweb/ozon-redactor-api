package org.ozonLabel.ozonApi.validation;

import org.ozonLabel.common.dto.label.ElementDto;
import org.ozonLabel.common.dto.label.LabelConfigDto;
import org.ozonLabel.common.dto.label.LayerDto;
import org.ozonLabel.common.exception.user.ValidationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LabelValidator {

    private static final Set<String> VALID_ELEMENT_TYPES = Set.of(
            "text", "image", "barcode", "date", "rectangle", "datamatrix"
    );

    private static final Set<String> VALID_BARCODE_TYPES = Set.of(
            "Code 128", "Code 39", "EAN-13", "EAN-8", "UPC-A"
    );

    private static final Set<String> VALID_DATE_TYPES = Set.of(
            "manufacture", "bestBefore", "shelfLife"
    );

    private static final BigDecimal MIN_SIZE = new BigDecimal("10");
    private static final BigDecimal MAX_SIZE = new BigDecimal("300");

    public void validate(LabelConfigDto config) {
        validateSize(config);
        validateLayers(config.getLayers());
        validateElements(config.getElements(), config.getLayers());
        validateDataMatrixLimit(config.getElements());
    }

    private void validateSize(LabelConfigDto config) {
        if (config.getWidth().compareTo(MIN_SIZE) < 0 || config.getWidth().compareTo(MAX_SIZE) > 0) {
            throw new ValidationException("Ширина должна быть от 10 до 300 мм");
        }
        if (config.getHeight().compareTo(MIN_SIZE) < 0 || config.getHeight().compareTo(MAX_SIZE) > 0) {
            throw new ValidationException("Высота должна быть от 10 до 300 мм");
        }
    }

    private void validateLayers(List<LayerDto> layers) {
        if (layers == null || layers.isEmpty()) {
            throw new ValidationException("Должен быть хотя бы один слой");
        }

        boolean hasBackgroundLayer = layers.stream()
                .anyMatch(l -> l.getId() != null && l.getId() == 0);

        if (!hasBackgroundLayer) {
            throw new ValidationException("Фоновый слой (id=0) обязателен");
        }
    }

    private void validateElements(List<ElementDto> elements, List<LayerDto> layers) {
        if (elements == null) return;

        Set<Integer> layerIds = layers.stream()
                .map(LayerDto::getId)
                .collect(Collectors.toSet());

        for (ElementDto element : elements) {
            if (!VALID_ELEMENT_TYPES.contains(element.getType())) {
                throw new ValidationException("Недопустимый тип элемента: " + element.getType());
            }

            if (!layerIds.contains(element.getLayerId())) {
                throw new ValidationException("Элемент ссылается на несуществующий слой: " + element.getLayerId());
            }

            validateElementByType(element);
        }
    }

    private void validateElementByType(ElementDto element) {
        switch (element.getType()) {
            case "barcode" -> {
                if (element.getBarcodeType() != null && !VALID_BARCODE_TYPES.contains(element.getBarcodeType())) {
                    throw new ValidationException("Недопустимый тип штрихкода: " + element.getBarcodeType());
                }
            }
            case "date" -> {
                if (element.getDateType() != null && !VALID_DATE_TYPES.contains(element.getDateType())) {
                    throw new ValidationException("Недопустимый тип даты: " + element.getDateType());
                }
            }
            case "image" -> {
                if (element.getImageUrl() == null && element.getImageId() == null) {
                    throw new ValidationException("Для изображения требуется imageUrl или imageId");
                }
            }
        }
    }

    private void validateDataMatrixLimit(List<ElementDto> elements) {
        if (elements == null) return;

        long count = elements.stream()
                .filter(e -> "datamatrix".equals(e.getType()))
                .count();

        if (count > 1) {
            throw new ValidationException("Максимум 1 DataMatrix на этикетку");
        }
    }
}
