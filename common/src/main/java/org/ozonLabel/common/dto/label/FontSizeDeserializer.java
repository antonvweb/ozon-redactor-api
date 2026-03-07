package org.ozonLabel.common.dto.label;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FontSizeDeserializer extends JsonDeserializer<BigDecimal> {
    
    private static final Pattern FONT_SIZE_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)?)(?:px|pt|em|rem)?$", Pattern.CASE_INSENSITIVE);
    
    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        // Если это уже число (без единиц измерения)
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            // Пытаемся извлечь число из строки вида "14px", "12pt" и т.д.
            Matcher matcher = FONT_SIZE_PATTERN.matcher(value.trim());
            if (matcher.matches()) {
                return new BigDecimal(matcher.group(1));
            }
            // Если не удалось распарсить, возвращаем null или выбрасываем исключение
            return null;
        }
    }
}
