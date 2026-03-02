-- Таблица для хранения кодов DataMatrix (Честный знак)
CREATE TABLE datamatrix_codes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    code TEXT NOT NULL,
    gtin VARCHAR(14),
    serial VARCHAR(50),
    is_used BOOLEAN DEFAULT FALSE,
    is_duplicate BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uk_datamatrix_company_code UNIQUE(company_id, code)
);

-- Индексы для оптимизации поиска
CREATE INDEX idx_dm_codes_product ON datamatrix_codes(product_id, is_used);
CREATE INDEX idx_dm_codes_company ON datamatrix_codes(company_id, code);
CREATE INDEX idx_dm_codes_used ON datamatrix_codes(is_used);
CREATE INDEX idx_dm_codes_product_unused ON datamatrix_codes(product_id, is_used) WHERE is_used = FALSE;

-- Комментарии
COMMENT ON TABLE datamatrix_codes IS 'Коды DataMatrix (Честный знак) для маркировки товаров';
COMMENT ON COLUMN datamatrix_codes.code IS 'Полный GS1 DataMatrix код';
COMMENT ON COLUMN datamatrix_codes.gtin IS 'GTIN (14 цифр) извлечённый из кода';
COMMENT ON COLUMN datamatrix_codes.serial IS 'Серийный номер из кода';
COMMENT ON COLUMN datamatrix_codes.is_used IS 'Флаг использования кода при печати';
COMMENT ON COLUMN datamatrix_codes.is_duplicate IS 'Флаг дубликата кода';
