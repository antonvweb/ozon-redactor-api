-- Добавление поля source_type в таблицу product_folders
ALTER TABLE product_folders ADD COLUMN source_type VARCHAR(20) DEFAULT 'MANUAL';

-- Добавление комментария
COMMENT ON COLUMN product_folders.source_type IS 'Тип источника данных: API, EXCEL, MANUAL, TEMPLATE';
