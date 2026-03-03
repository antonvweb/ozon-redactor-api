# BACKEND PROMPT — Доработка Spring Boot микросервисов

## Контекст проекта

Проект: **Label Editor 365** — сервис создания этикеток для маркетплейса Ozon.

**Архитектура бэкенда:**
- 3 Spring Boot микросервиса: `authService` (порт 9147), `userService` (порт 7293), `ozonApi` (порт 6482)
- БД: PostgreSQL (`ozon_label_bd`), кэш: Redis
- Общий модуль `common` с DTO, интерфейсами сервисов, исключениями
- JPA-сущности, Flyway-миграции, Spring Security + JWT в HttpOnly cookies
- Label entity хранит конфигурацию как `JSONB` (config: слои, элементы, размеры)
- Модуль `ozonApi` содержит: `LabelController`, `FolderController`, `OzonController`, `LabelServiceImpl`, `FolderServiceImpl`
- Доступ проверяется через `companyService.checkAccess(userEmail, companyOwnerId)`

**Существующие эндпоинты:**
- Labels: `POST/GET/PUT/DELETE /api/labels`, `GET /api/labels/product/{productId}`, `POST /api/labels/{id}/duplicate`, `POST /api/labels/batch`
- Folders: `POST/GET/PUT/DELETE /api/folders`, `GET /api/folders/tree`, `POST /api/folders/move-products`, `GET /api/folders/{id}/path`
- LabelSizes: CRUD `/api/label-sizes` (в userService)

**Структура Label config (JSONB):**
```json
{
  "width": 58, "height": 40, "unit": "mm",
  "layers": [
    { "id": 0, "name": "Canvas (Background)", "locked": true, "visible": true, "layerType": "static" },
    { "id": 1, "name": "Слой 1", "locked": false, "visible": true, "layerType": "dynamic", "columnName": "barcode" }
  ],
  "elements": [
    { "id": "text-123", "type": "text", "layerId": 1, "x": 10, "y": 15, "width": 200, "height": 30, "content": "...", "style": {...} },
    { "id": "barcode-456", "type": "barcode", "layerId": 1, "x": 20, "y": 100, "width": 150, "height": 40, "barcodeType": "Code 128", "content": "1234567890123" },
    { "id": "datamatrix-789", "type": "datamatrix", "layerId": 1, "x": 5, "y": 5, "width": 48, "height": 48, "content": "..." }
  ]
}
```

**Структура ProductFolder entity:**
```java
@Entity @Table(name = "product_folders")
public class ProductFolder {
    Long id, userId, parentFolderId;
    String name, color, icon;
    Integer position;
    // sourceType пока нет в entity — нужно добавить
}
```

---

## ЗАДАЧИ (реализовать всё ниже)

### 1. Генерация PDF для печати этикеток

Создай полноценный модуль генерации PDF в `ozonApi`.

**Что нужно:**

a) **Эндпоинт** `POST /api/labels/print`:
```java
@PostMapping("/print")
public ResponseEntity<byte[]> printLabels(
    @RequestParam Long companyOwnerId,
    @RequestBody PrintRequest dto, // { productIds: Long[], copies: Map<Long, Integer>, separatorType: "DARK"|"LIGHT"|"NONE" }
    Authentication auth
)
```
- Возвращает PDF-файл (`Content-Type: application/pdf`)
- Для каждого productId берёт этикетку из БД, рендерит по config
- `copies` — сколько копий каждой этикетки (из колонки «Количество» таблицы)
- Между разными SKU вставляет разделительную полосу (тёмную или светлую) если `separatorType != "NONE"`

b) **PDF-рендеринг** (используй библиотеку `iText 7` или `Apache PDFBox`):
- Размер страницы = labelSize из config (width × height мм)
- Рендеринг элементов по типу:
  - `text` → текст с fontFamily, fontSize, fontWeight, italic, underline, textAlign, color, backgroundColor, lineHeight, letterSpacing, inverted (белый на чёрном)
  - `barcode` → штрихкод Code128/EAN-13/EAN-8 через библиотеку (iText barcode или ZXing)
  - `image` → вставка изображения по imageUrl
  - `date` → если `useCurrentDate=true` подставить текущую дату в нужном формате (ДД.ММ.ГГГГ, ДД.ММ.ГГ, ГГГГ-ММ-ДД), если shelfLife — рассчитать дату «годен до» = дата изготовления + срок годности
  - `datamatrix` → GS1 DataMatrix (FNC1 ASCII 232, GS ASCII 29)
  - `rectangle` → прямоугольник с заливкой
- Элементы с `visible: false` — не рендерить
- Элементы позиционируются по координатам (x, y) в пикселях, конвертировать: 1 мм ≈ 2.835 pt (72 dpi)
- Учитывать `rotation` элемента (0, 90, 180, 270)
- Учитывать `zIndex` — порядок наложения

c) **Печать листа подбора** `POST /api/labels/pick-list`:
```java
@PostMapping("/pick-list")
public ResponseEntity<byte[]> printPickList(
    @RequestParam Long companyOwnerId,
    @RequestBody PickListRequest dto, // { productIds: Long[] }
    Authentication auth
)
```
- Генерирует PDF-таблицу: фото | название | штрихкод | артикул | количество
- Формат A4, таблица на всю ширину

d) **Сервис:**
```java
public interface PrintService {
    byte[] generateLabelsPdf(String userEmail, Long companyOwnerId, PrintRequest request);
    byte[] generatePickListPdf(String userEmail, Long companyOwnerId, PickListRequest request);
}
```

---

### 2. Модуль «Честный знак» (DataMatrix)

a) **Entity** `DataMatrixCode`:
```java
@Entity @Table(name = "datamatrix_codes")
public class DataMatrixCode {
    Long id;
    Long userId, companyId, productId;
    String code;           // полный GS1 код
    String gtin;           // GTIN из кода
    String serial;         // серийный номер
    boolean isUsed;        // использован при печати
    boolean isDuplicate;   // дубликат
    LocalDateTime usedAt;
    LocalDateTime createdAt;
}
```

b) **Эндпоинт загрузки кодов** `POST /api/datamatrix/upload`:
```java
@PostMapping("/upload")
public ResponseEntity<DataMatrixUploadResponse> uploadCodes(
    @RequestParam Long companyOwnerId,
    @RequestParam Long productId,
    @RequestParam MultipartFile file, // PDF или CSV
    @RequestParam(defaultValue = "true") boolean checkDuplicates,
    Authentication auth
)
```
- Парсинг PDF: извлечь все DataMatrix коды из PDF (используй ZXing или Google's ZXing для декодирования)
- Парсинг CSV: построчное чтение кодов
- Если `checkDuplicates=true` — проверить каждый код на дубликат в БД для этого productId и для всех продуктов компании
- Response: `{ total: 150, new: 142, duplicates: 8, codes: [...] }`

c) **CRUD эндпоинты:**
- `GET /api/datamatrix/product/{productId}?companyOwnerId=X` — получить коды для продукта (с пагинацией)
- `GET /api/datamatrix/product/{productId}/stats?companyOwnerId=X` — `{ total: 150, remaining: 142, used: 8 }`
- `DELETE /api/datamatrix/{codeId}?companyOwnerId=X` — удалить код

d) **Списание кодов при печати:**
В `PrintService.generateLabelsPdf()` — при рендеринге этикетки с DataMatrix элементом:
- Взять следующий неиспользованный код (`isUsed=false`) для этого productId
- Отрендерить GS1 DataMatrix
- Пометить код как `isUsed=true`, записать `usedAt`
- Если кодов не хватает — вернуть ошибку 400 с сообщением «Недостаточно кодов ЧЗ для продукта X. Остаток: Y, требуется: Z»

e) **GS1 формат:**
- DataMatrix содержит: FNC1 (ASCII 232) + AI(01) + GTIN(14) + AI(21) + Serial + GS (ASCII 29) + AI(93) + Verification
- Метод `parseGS1Code(String raw)` → `{ gtin, serial, verificationKey }`

f) **Flyway-миграция:**
```sql
CREATE TABLE datamatrix_codes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    company_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    code TEXT NOT NULL,
    gtin VARCHAR(14),
    serial VARCHAR(50),
    is_used BOOLEAN DEFAULT FALSE,
    is_duplicate BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(company_id, code)
);
CREATE INDEX idx_dm_codes_product ON datamatrix_codes(product_id, is_used);
CREATE INDEX idx_dm_codes_company ON datamatrix_codes(company_id, code);
```

---

### 3. Система шаблонов этикеток

a) **Entity** `LabelTemplate`:
```java
@Entity @Table(name = "label_templates")
public class LabelTemplate {
    Long id;
    String name;           // "Стандартная", "Ценник", "Почтовая", "Складская"
    boolean isSystem;      // системный шаблон (не удаляется)
    Long companyId;        // null для системных
    Long userId;
    BigDecimal width, height;
    String unit = "mm";
    @JdbcTypeCode(SqlTypes.JSON)
    String config;         // тот же формат что у Label
    String previewUrl;     // URL превью изображения
    LocalDateTime createdAt, updatedAt;
}
```

b) **Эндпоинты** `LabelTemplateController`:
- `GET /api/templates?companyOwnerId=X` — все шаблоны (системные + пользовательские)
- `POST /api/templates?companyOwnerId=X` — создать пользовательский шаблон
- `PUT /api/templates/{id}?companyOwnerId=X` — обновить
- `DELETE /api/templates/{id}?companyOwnerId=X` — удалить (только не системные)
- `POST /api/templates/{id}/apply?companyOwnerId=X&productId=Y` — применить шаблон к продукту (создаёт Label из шаблона)

c) **Системные шаблоны** (seed в миграции):
1. **Стандартная** (58×40): штрихкод 75% ширины снизу, название сверху, артикул с инверсией
2. **Ценник** (58×40): крупная цена по центру, название, штрихкод мелкий
3. **Почтовая** (100×150): адрес, штрихкод, QR-код
4. **Складская** (75×120): крупный штрихкод, артикул, название, дата

---

### 4. Экспорт этикеток в Excel

Эндпоинт `POST /api/labels/export`:
```java
@PostMapping("/export")
public ResponseEntity<byte[]> exportLabels(
    @RequestParam Long companyOwnerId,
    @RequestBody ExportRequest dto, // { productIds: Long[], format: "EXCEL"|"PDF", includeLayers: true }
    Authentication auth
)
```

**Для Excel (используй Apache POI):**
- Каждый динамический слой (layerType="dynamic") → колонка в Excel, заголовок = `layer.columnName` или `layer.name`
- Каждый продукт → строка
- Значения берутся из `element.content` для элементов этого слоя
- Статические слои → не экспортируются
- Добавить колонки: «Штрихкод», «Артикул», «Название», «Количество»

**Для PDF:**
- Переиспользовать `PrintService.generateLabelsPdf()`

---

### 5. Валидация перемещения папок по типу

В `FolderServiceImpl.moveProductsToFolder()` добавить проверку:

a) Добавить поле `sourceType` в entity `ProductFolder`:
```java
@Enumerated(EnumType.STRING)
@Column(name = "source_type")
private SourceType sourceType; // API, EXCEL, MANUAL, TEMPLATE
```

b) Flyway-миграция: `ALTER TABLE product_folders ADD COLUMN source_type VARCHAR(20) DEFAULT 'MANUAL';`

c) При перемещении товаров проверять:
- Если исходная папка `API` → можно перемещать только в подпапки `API`-папок или в корень
- Если исходная папка `EXCEL` → можно перемещать только в подпапки `EXCEL`-папок или в корень
- Если `MANUAL` → можно в любую папку
- При нарушении → `throw new ValidationException("Товары из API-папки можно перемещать только в подпапки API")`

---

### 6. Глобальная видимость слоёв для папки

Эндпоинт `PATCH /api/folders/{folderId}/layer-visibility`:
```java
@PatchMapping("/{folderId}/layer-visibility")
public ResponseEntity<ApiResponse> updateLayerVisibility(
    @PathVariable Long folderId,
    @RequestParam Long companyOwnerId,
    @RequestBody LayerVisibilityRequest dto, // { layerId: 1, visible: false }
    Authentication auth
)
```
- Находит все этикетки продуктов в этой папке
- Для каждой этикетки: парсит config JSONB, находит слой с `layerId`, ставит `visible = dto.visible`, сохраняет обратно
- Batch update через `labelRepository.saveAll()`

---

### 7. Эндпоинт обновления данных папки

`POST /api/folders/{folderId}/refresh`:
- Если папка `sourceType=API` — пересинхронизировать товары из Ozon API для этой папки
- Если `sourceType=EXCEL` — вернуть 400 с инструкцией перезагрузить файл
- Переиспользовать `OzonService.syncProducts()` с фильтром по productIds из папки

---

## Зависимости для pom.xml (ozonApi)

```xml
<!-- PDF генерация -->
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itext7-core</artifactId>
    <version>7.2.5</version>
</dependency>
<!-- Штрихкоды -->
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>3.5.2</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>3.5.2</version>
</dependency>
<!-- Excel -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

## Важно

- Все эндпоинты требуют `Authentication auth` и `companyOwnerId`
- Используй `companyService.checkAccess(userEmail, companyOwnerId)` для проверки прав
- Следуй существующему паттерну: Controller → Service interface (в common) → ServiceImpl (в ozonApi)
- Flyway-миграции в `ozonApi/src/main/resources/db/migration/`
- Логирование через `@Slf4j`
- Все DTO в `common/src/main/java/org/ozonLabel/common/dto/`
- Русские сообщения об ошибках
