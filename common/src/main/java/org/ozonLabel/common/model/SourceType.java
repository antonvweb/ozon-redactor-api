package org.ozonLabel.common.model;

/**
 * Тип источника данных для папки продуктов
 */
public enum SourceType {
    /**
     * Товары загружены из API маркетплейса (Ozon)
     */
    API,

    /**
     * Товары загружены из Excel файла
     */
    EXCEL,

    /**
     * Товары добавлены вручную
     */
    MANUAL,

    /**
     * Товары созданы из шаблона
     */
    TEMPLATE
}
