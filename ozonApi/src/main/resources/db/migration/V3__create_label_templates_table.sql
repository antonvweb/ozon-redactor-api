-- Таблица для хранения шаблонов этикеток
CREATE TABLE label_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    is_system BOOLEAN DEFAULT FALSE NOT NULL,
    company_id BIGINT,
    user_id BIGINT NOT NULL,
    width DECIMAL(10,2) NOT NULL,
    height DECIMAL(10,2) NOT NULL,
    unit VARCHAR(10) DEFAULT 'mm' NOT NULL,
    config JSONB NOT NULL,
    preview_url TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP
);

-- Индексы для оптимизации поиска
CREATE INDEX idx_templates_system ON label_templates(is_system);
CREATE INDEX idx_templates_company ON label_templates(company_id);
CREATE INDEX idx_templates_user ON label_templates(user_id);

-- Комментарии
COMMENT ON TABLE label_templates IS 'Шаблоны этикеток для быстрого создания';
COMMENT ON COLUMN label_templates.is_system IS 'Флаг системного шаблона (не удаляется)';
COMMENT ON COLUMN label_templates.company_id IS 'ID компании для пользовательских шаблонов (NULL для системных)';
COMMENT ON COLUMN label_templates.config IS 'JSONB конфигурация этикетки (слои, элементы)';

-- Seed системных шаблонов

-- 1. Стандартная (58×40): штрихкод 75% ширины снизу, название сверху, артикул с инверсией
INSERT INTO label_templates (name, is_system, user_id, width, height, config) VALUES
('Стандартная', true, 1, 58, 40, '{
  "width": 58,
  "height": 40,
  "unit": "mm",
  "layers": [
    {"id": 0, "name": "Canvas (Background)", "locked": true, "visible": true, "layerType": "static"},
    {"id": 1, "name": "Слой 1", "locked": false, "visible": true, "layerType": "dynamic", "columnName": "barcode"}
  ],
  "elements": [
    {"id": "text-name", "type": "text", "layerId": 0, "x": 5, "y": 2, "width": 48, "height": 8, "content": "Название товара", "style": {"fontSize": 10, "fontFamily": "Arial", "textAlign": "center"}},
    {"id": "barcode-main", "type": "barcode", "layerId": 1, "x": 5, "y": 25, "width": 43, "height": 12, "barcodeType": "Code 128", "content": "1234567890123"},
    {"id": "text-article", "type": "text", "layerId": 0, "x": 5, "y": 12, "width": 48, "height": 6, "content": "Артикул: 12345", "style": {"fontSize": 8, "fontFamily": "Arial", "inverted": true}}
  ]
}');

-- 2. Ценник (58×40): крупная цена по центру, название, штрихкод мелкий
INSERT INTO label_templates (name, is_system, user_id, width, height, config) VALUES
('Ценник', true, 1, 58, 40, '{
  "width": 58,
  "height": 40,
  "unit": "mm",
  "layers": [
    {"id": 0, "name": "Canvas (Background)", "locked": true, "visible": true, "layerType": "static"},
    {"id": 1, "name": "Слой 1", "locked": false, "visible": true, "layerType": "dynamic", "columnName": "price"}
  ],
  "elements": [
    {"id": "text-name", "type": "text", "layerId": 0, "x": 5, "y": 2, "width": 48, "height": 6, "content": "Название товара", "style": {"fontSize": 8, "fontFamily": "Arial", "textAlign": "center"}},
    {"id": "text-price", "type": "text", "layerId": 1, "x": 5, "y": 12, "width": 48, "height": 18, "content": "999.99 ₽", "style": {"fontSize": 24, "fontFamily": "Arial", "fontWeight": "bold", "textAlign": "center"}},
    {"id": "barcode-small", "type": "barcode", "layerId": 0, "x": 10, "y": 32, "width": 38, "height": 6, "barcodeType": "EAN-13", "content": "1234567890123"}
  ]
}');

-- 3. Почтовая (100×150): адрес, штрихкод, QR-код
INSERT INTO label_templates (name, is_system, user_id, width, height, config) VALUES
('Почтовая', true, 1, 100, 150, '{
  "width": 100,
  "height": 150,
  "unit": "mm",
  "layers": [
    {"id": 0, "name": "Canvas (Background)", "locked": true, "visible": true, "layerType": "static"},
    {"id": 1, "name": "Слой 1", "locked": false, "visible": true, "layerType": "dynamic", "columnName": "address"}
  ],
  "elements": [
    {"id": "text-address", "type": "text", "layerId": 1, "x": 10, "y": 10, "width": 80, "height": 60, "content": "Адрес получателя", "style": {"fontSize": 12, "fontFamily": "Arial"}},
    {"id": "barcode-main", "type": "barcode", "layerId": 0, "x": 10, "y": 80, "width": 80, "height": 20, "barcodeType": "Code 128", "content": "TRACK123456789"},
    {"id": "qr-code", "type": "qrcode", "layerId": 0, "x": 10, "y": 110, "width": 30, "height": 30, "content": "https://track.example.com/TRACK123456789"}
  ]
}');

-- 4. Складская (75×120): крупный штрихкод, артикул, название, дата
INSERT INTO label_templates (name, is_system, user_id, width, height, config) VALUES
('Складская', true, 1, 75, 120, '{
  "width": 75,
  "height": 120,
  "unit": "mm",
  "layers": [
    {"id": 0, "name": "Canvas (Background)", "locked": true, "visible": true, "layerType": "static"},
    {"id": 1, "name": "Слой 1", "locked": false, "visible": true, "layerType": "dynamic", "columnName": "article"}
  ],
  "elements": [
    {"id": "text-name", "type": "text", "layerId": 0, "x": 5, "y": 5, "width": 65, "height": 15, "content": "Название товара", "style": {"fontSize": 14, "fontFamily": "Arial", "fontWeight": "bold"}},
    {"id": "text-article", "type": "text", "layerId": 1, "x": 5, "y": 25, "width": 65, "height": 10, "content": "Артикул: 12345", "style": {"fontSize": 12, "fontFamily": "Arial"}},
    {"id": "barcode-large", "type": "barcode", "layerId": 0, "x": 5, "y": 45, "width": 65, "height": 30, "barcodeType": "Code 128", "content": "1234567890123"},
    {"id": "text-date", "type": "date", "layerId": 0, "x": 5, "y": 85, "width": 65, "height": 8, "content": "Дата: ДД.ММ.ГГГГ", "style": {"fontSize": 10, "fontFamily": "Arial"}, "dateSettings": {"format": "DD.MM.YYYY", "useCurrentDate": true}}
  ]
}');
