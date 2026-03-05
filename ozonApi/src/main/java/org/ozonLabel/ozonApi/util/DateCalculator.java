package org.ozonLabel.ozonApi.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class DateCalculator {

    /**
     * Расчёт даты «годен до» на основе даты изготовления, значения и единицы срока годности.
     * Учитывает реальный календарь (високосные годы, количество дней в месяце).
     *
     * @param manufactureDate дата изготовления
     * @param value значение срока годности
     * @param unit единица измерения: "day", "month", "year"
     * @return дата «годен до»
     */
    public LocalDate calculateBestBefore(LocalDate manufactureDate, Integer value, String unit) {
        if (manufactureDate == null || value == null || unit == null) {
            return null;
        }

        return switch (unit) {
            case "day" -> manufactureDate.plusDays(value);
            case "month" -> manufactureDate.plusMonths(value);
            case "year" -> manufactureDate.plusYears(value);
            default -> manufactureDate;
        };
    }

    /**
     * Форматирование срока годности с учётом склонения по числу.
     *
     * @param value числовое значение срока годности
     * @param unit единица измерения: "day", "month", "year"
     * @return отформатированная строка, например "1 день", "2 дня", "5 дней"
     */
    public String formatShelfLife(Integer value, String unit) {
        if (value == null || unit == null) {
            return "";
        }

        String formattedUnit = switch (unit) {
            case "day" -> declension(value, "день", "дня", "дней");
            case "month" -> declension(value, "месяц", "месяца", "месяцев");
            case "year" -> declension(value, "год", "года", "лет");
            default -> String.valueOf(value);
        };

        return value + " " + formattedUnit;
    }

    /**
     * Склонение слов по числу (правила русского языка).
     *
     * @param number число
     * @param one форма для 1 (например, "день")
     * @param few форма для 2-4 (например, "дня")
     * @param many форма для 5+ (например, "дней")
     * @return правильная форма слова
     */
    private String declension(int number, String one, String few, String many) {
        int lastDigit = number % 10;
        int lastTwoDigits = number % 100;

        // Исключения для 11-14
        if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
            return many;
        }

        return switch (lastDigit) {
            case 1 -> one;
            case 2, 3, 4 -> few;
            default -> many;
        };
    }

    /**
     * Получение DateTimeFormatter по строковому формату.
     *
     * @param format строковый формат: "DD.MM.YYYY", "DD.MM.YY", "YYYY-MM-DD"
     * @return DateTimeFormatter
     */
    public DateTimeFormatter getFormatter(String format) {
        if (format == null || format.isEmpty()) {
            return DateTimeFormatter.ofPattern("dd.MM.yyyy");
        }

        return switch (format) {
            case "DD.MM.YYYY" -> DateTimeFormatter.ofPattern("dd.MM.yyyy");
            case "DD.MM.YY" -> DateTimeFormatter.ofPattern("dd.MM.yy");
            case "YYYY-MM-DD" -> DateTimeFormatter.ofPattern("yyyy-MM-dd");
            default -> DateTimeFormatter.ofPattern("dd.MM.yyyy");
        };
    }
}
