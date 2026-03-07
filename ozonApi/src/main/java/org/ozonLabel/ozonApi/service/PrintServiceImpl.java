package org.ozonLabel.ozonApi.service;

import com.itextpdf.barcodes.Barcode128;
import com.itextpdf.barcodes.BarcodeEAN;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.borders.SolidBorder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.label.*;
import org.ozonLabel.common.exception.user.ValidationException;
import org.ozonLabel.common.service.datamatrix.DataMatrixService;
import org.ozonLabel.common.service.label.LabelService;
import org.ozonLabel.common.service.label.PrintService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.ozonApi.util.DateCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrintServiceImpl implements PrintService {

    private final LabelService labelService;
    private final CompanyService companyService;
    private final DataMatrixService dataMatrixService;
    private final DateCalculator dateCalculator;

    // Константы для конвертации мм в пункты (1 мм ≈ 2.835 pt при 72 dpi)
    private static final double MM_TO_POINTS = 2.83464567;

    @Override
    @Transactional
    public PrintResponse generateLabelsPdf(String userEmail, Long companyOwnerId, PrintRequest request) {
        companyService.checkAccess(userEmail, companyOwnerId);

        int totalLabels = 0;
        int dataMatrixCodesUsed = 0;
        List<Long> productsMissingDmCodes = new ArrayList<>();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);

            List<Long> productIds = request.getProductIds();
            Map<Long, Integer> copies = request.getCopies() != null ? request.getCopies() : new HashMap<>();
            String separatorType = request.getSeparatorType() != null ? request.getSeparatorType() : "NONE";

            boolean firstLabel = true;
            Long lastProductId = null;

            for (Long productId : productIds) {
                try {
                    LabelResponseDto label;
                    try {
                        label = labelService.getLabelByProductId(userEmail, companyOwnerId, productId);
                    } catch (Exception e) {
                        log.warn("Этикетка для продукта {} не найдена, пропускаем", productId);
                        continue;
                    }

                    // Защита от null
                    if (label == null) {
                        log.warn("Этикетка для товара {} не найдена, пропускаем", productId);
                        continue;
                    }
                    if (label.getConfig() == null) {
                        log.warn("Этикетка для товара {} не имеет конфигурации, пропускаем", productId);
                        continue;
                    }

                    int copiesCount = copies.getOrDefault(productId, 1);
                    if (copiesCount <= 0) copiesCount = 1;

                    for (int i = 0; i < copiesCount; i++) {
                        if (!firstLabel && !separatorType.equals("NONE") && !productId.equals(lastProductId)) {
                            addSeparator(pdf, separatorType);
                        }

                        totalLabels++;
                        int[] codesUsedInfo = generateLabelPage(pdf, label, userEmail, companyOwnerId, productId);
                        dataMatrixCodesUsed += codesUsedInfo[0];
                        if (codesUsedInfo[1] > 0) {
                            productsMissingDmCodes.add(productId);
                        }

                        firstLabel = false;
                    }

                    lastProductId = productId;

                } catch (Exception e) {
                    log.error("Ошибка рендеринга этикетки для товара {}: {}", productId, e.getMessage(), e);
                    // Продолжаем с остальными — не прерываем весь экспорт из-за одного товара
                }
            }

            pdf.close();
            log.info("Сгенерирован PDF с этикетками для {} продуктов, всего страниц: {}, списано DataMatrix кодов: {}",
                    productIds.size(), totalLabels, dataMatrixCodesUsed);

            return PrintResponse.builder()
                    .pdfData(baos.toByteArray())
                    .totalLabels(totalLabels)
                    .dataMatrixCodesUsed(dataMatrixCodesUsed)
                    .productsMissingDmCodes(productsMissingDmCodes)
                    .build();

        } catch (Exception e) {
            log.error("Ошибка генерации PDF: {}", e.getMessage(), e);
            throw new ValidationException("Ошибка генерации PDF: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePickListPdf(String userEmail, Long companyOwnerId, PickListRequest request) {
        companyService.checkAccess(userEmail, companyOwnerId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);

            PdfPage page = pdf.addNewPage(PageSize.A4);
            PdfCanvas pdfCanvas = new PdfCanvas(page);
            Canvas canvas = new Canvas(pdfCanvas, page.getPageSize());

            Paragraph title = new Paragraph("Лист подбора")
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER);
            canvas.showTextAligned(title, 297, 820, TextAlignment.CENTER);

            float yPosition = 780;
            String[] headers = {"Фото", "Название", "Штрихкод", "Артикул", "Кол-во"};

            float xPosition = 20;
            for (String header : headers) {
                Paragraph p = new Paragraph(header).setFontSize(10).setBold();
                canvas.showTextAligned(p, xPosition + 5, yPosition, TextAlignment.LEFT);
                xPosition += 100;
            }

            pdfCanvas.rectangle(20, yPosition - 5, 574, 1);
            pdfCanvas.stroke();

            canvas.close();
            pdf.close();

            log.info("Сгенерирован лист подбора для {} продуктов", request.getProductIds().size());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка генерации листа подбора: {}", e.getMessage(), e);
            throw new ValidationException("Ошибка генерации листа подбора: " + e.getMessage());
        }
    }

    private int[] generateLabelPage(PdfDocument pdf, LabelResponseDto label, String userEmail, Long companyOwnerId, Long productId) {
        float widthPt = label.getWidth().floatValue() * (float) MM_TO_POINTS;
        float heightPt = label.getHeight().floatValue() * (float) MM_TO_POINTS;

        PdfPage page = pdf.addNewPage(new PageSize(widthPt, heightPt));
        PdfCanvas pdfCanvas = new PdfCanvas(page);
        Canvas canvas = new Canvas(pdfCanvas, page.getPageSize());

        int dataMatrixCodesUsed = 0;
        int missingCodesCount = 0;

        try {
            LabelConfigDto config = label.getConfig();

            List<ElementDto> elements = new ArrayList<>(config.getElements());
            elements.sort(Comparator.comparingInt(e -> e.getZIndex() != null ? e.getZIndex() : 0));

            for (ElementDto element : elements) {
                if (element.getVisible() != null && !element.getVisible()) {
                    continue;
                }
                int[] renderResult = renderElement(canvas, element, config, userEmail, companyOwnerId, productId);
                dataMatrixCodesUsed += renderResult[0];
                missingCodesCount += renderResult[1];
            }

        } catch (Exception e) {
            log.error("Ошибка рендеринга этикетки: {}", e.getMessage(), e);
        } finally {
            canvas.close();
        }

        return new int[]{dataMatrixCodesUsed, missingCodesCount};
    }

    private int[] renderElement(Canvas canvas, ElementDto element, LabelConfigDto config, String userEmail, Long companyOwnerId, Long productId) {
        // Защита от null
        if (element == null || element.getType() == null) {
            return new int[]{0, 0};
        }
        if (element.getX() == null || element.getY() == null ||
            element.getWidth() == null || element.getHeight() == null) {
            log.warn("Элемент {} имеет null координаты, пропускаем", element.getId());
            return new int[]{0, 0};
        }

        String type = element.getType();

        float x = element.getX().floatValue() * (float) MM_TO_POINTS;
        float y = element.getY().floatValue() * (float) MM_TO_POINTS;
        float width = element.getWidth().floatValue() * (float) MM_TO_POINTS;
        float height = element.getHeight().floatValue() * (float) MM_TO_POINTS;

        // Получаем rotation (по умолчанию 0)
        Integer rotation = element.getRotation() != null ? element.getRotation().intValue() : 0;

        int dataMatrixCodesUsed = 0;
        int missingCodesCount = 0;

        switch (type) {
            case "text":
                renderText(canvas, element, x, y, width, height, rotation);
                break;
            case "barcode":
                renderBarcode(canvas, element, x, y, width, height, rotation);
                break;
            case "datamatrix":
                int[] dmResult = renderDataMatrix(canvas, element, x, y, width, height, userEmail, companyOwnerId, productId);
                dataMatrixCodesUsed += dmResult[0];
                missingCodesCount += dmResult[1];
                break;
            case "qrcode":
                renderQRCode(canvas, element, x, y, width, height);
                break;
            case "date":
                renderDate(canvas, element, x, y, width, height, rotation);
                break;
            case "image":
                renderImage(canvas, element, x, y, width, height, rotation);
                break;
            case "rectangle":
                renderRectangle(canvas, element, x, y, width, height, rotation);
                break;
        }

        return new int[]{dataMatrixCodesUsed, missingCodesCount};
    }

    private void renderText(Canvas canvas, ElementDto element, float x, float y, float width, float height, Integer rotation) {
        String content = element.getContent() != null ? element.getContent() : "";
        TextStyleDto style = element.getStyle();

        if (style == null) {
            style = TextStyleDto.builder().build();
        }

        try {
            PdfFont font = createFont(style);
            float fontSize = style.getFontSize() != null ? style.getFontSize().floatValue() : 12;

            Paragraph paragraph = new Paragraph(content)
                    .setFont(font)
                    .setFontSize(fontSize);

            // Text alignment
            if (style.getTextAlign() != null) {
                switch (style.getTextAlign()) {
                    case "left": paragraph.setTextAlignment(TextAlignment.LEFT); break;
                    case "center": paragraph.setTextAlignment(TextAlignment.CENTER); break;
                    case "right": paragraph.setTextAlignment(TextAlignment.RIGHT); break;
                }
            }

            // Цвет текста
            if (style.getColor() != null) {
                Color textColor = parseColor(style.getColor());
                paragraph.setFontColor(textColor);
            }

            // Background color
            if (style.getBackgroundColor() != null) {
                Color bgColor = parseColor(style.getBackgroundColor());
                paragraph.setBackgroundColor(bgColor);
            }

            // Inverted (белый текст на чёрном фоне)
            if (style.getInverted() != null && style.getInverted()) {
                paragraph.setFontColor(new DeviceRgb(255, 255, 255));
                paragraph.setBackgroundColor(new DeviceRgb(0, 0, 0));
            }

            // Line height
            if (style.getLineHeight() != null) {
                paragraph.setMultipliedLeading(style.getLineHeight().floatValue());
            }

            // Letter spacing
            if (style.getLetterSpacing() != null) {
                paragraph.setCharacterSpacing(style.getLetterSpacing().floatValue());
            }

            // Bold/Italic/Underline будут обрабатываться через шрифт
            // Для полной поддержки нужны разные файлы шрифтов
            if (style.getUnderline() != null && style.getUnderline()) {
                paragraph.setUnderline();
            }

            // Rotation
            if (rotation != null && rotation != 0) {
                paragraph.setRotationAngle(Math.toRadians(rotation));
            }

            paragraph.setFixedPosition(x, y, width);
            canvas.add(paragraph);
        } catch (Exception e) {
            log.error("Ошибка рендеринга текста: {}", e.getMessage());
        }
    }

    private void renderBarcode(Canvas canvas, ElementDto element, float x, float y, float width, float height, Integer rotation) {
        String content = element.getContent() != null ? element.getContent() : "";
        String barcodeType = element.getBarcodeType() != null ? element.getBarcodeType() : "Code 128";

        if (content.isEmpty()) {
            log.warn("Пустое содержимое штрихкода");
            return;
        }

        try {
            PdfDocument pdfDocument = canvas.getPdfDocument();
            PdfPage page = pdfDocument.getLastPage();
            PdfCanvas pdfCanvas = new PdfCanvas(page);

            switch (barcodeType) {
                case "Code 128":
                case "Code128":
                    Barcode128 barcode128 = new Barcode128(pdfDocument);
                    barcode128.setCode(content);
                    barcode128.setSize(8);
                    barcode128.setBaseline(10);

                    // Рендерим штрихкод
                    com.itextpdf.layout.element.Image barcodeImage128 = new com.itextpdf.layout.element.Image(
                        barcode128.createFormXObject(pdfDocument));
                    barcodeImage128.setFixedPosition(x, y);
                    barcodeImage128.scaleToFit(width, height);

                    if (rotation != null && rotation != 0) {
                        barcodeImage128.setRotationAngle(Math.toRadians(rotation));
                    }

                    canvas.add(barcodeImage128);
                    break;

                case "EAN-13":
                case "EAN13":
                    if (content.length() != 13) {
                        log.warn("EAN-13 требует 13 цифр, получено: {}", content.length());
                        return;
                    }
                    BarcodeEAN barcodeEAN13 = new BarcodeEAN(pdfDocument);
                    barcodeEAN13.setCodeType(BarcodeEAN.EAN13);
                    barcodeEAN13.setCode(content);

                    com.itextpdf.layout.element.Image barcodeImageEAN13 = new com.itextpdf.layout.element.Image(
                        barcodeEAN13.createFormXObject(pdfDocument));
                    barcodeImageEAN13.setFixedPosition(x, y);
                    barcodeImageEAN13.scaleToFit(width, height);

                    if (rotation != null && rotation != 0) {
                        barcodeImageEAN13.setRotationAngle(Math.toRadians(rotation));
                    }

                    canvas.add(barcodeImageEAN13);
                    break;

                case "EAN-8":
                case "EAN8":
                    if (content.length() != 8) {
                        log.warn("EAN-8 требует 8 цифр, получено: {}", content.length());
                        return;
                    }
                    BarcodeEAN barcodeEAN8 = new BarcodeEAN(pdfDocument);
                    barcodeEAN8.setCodeType(BarcodeEAN.EAN8);
                    barcodeEAN8.setCode(content);

                    com.itextpdf.layout.element.Image barcodeImageEAN8 = new com.itextpdf.layout.element.Image(
                        barcodeEAN8.createFormXObject(pdfDocument));
                    barcodeImageEAN8.setFixedPosition(x, y);
                    barcodeImageEAN8.scaleToFit(width, height);

                    if (rotation != null && rotation != 0) {
                        barcodeImageEAN8.setRotationAngle(Math.toRadians(rotation));
                    }

                    canvas.add(barcodeImageEAN8);
                    break;

                default:
                    log.warn("Неподдерживаемый тип штрихкода: {}", barcodeType);
                    // Фоллбэк на текст
                    Paragraph fallbackText = new Paragraph(content)
                            .setFontSize(8)
                            .setFixedPosition(x, y, width);
                    canvas.add(fallbackText);
            }
        } catch (Exception e) {
            log.error("Ошибка рендеринга штрихкода {}: {}", barcodeType, e.getMessage(), e);
            // Фоллбэк: показываем текст вместо штрихкода
            try {
                Paragraph fallbackText = new Paragraph(content)
                        .setFontSize(8)
                        .setFixedPosition(x, y, width);
                canvas.add(fallbackText);
            } catch (Exception ex) {
                log.error("Ошибка фоллбэка для штрихкода: {}", ex.getMessage());
            }
        }
    }

    private int[] renderDataMatrix(Canvas canvas, ElementDto element, float x, float y, float width, float height,
                                    String userEmail, Long companyOwnerId, Long productId) {
        try {
            // Проверяем есть ли привязка к конкретному файлу
            Long fileId = element.getDataMatrixFileId();
            
            Optional<String> codeOpt;
            if (fileId != null) {
                // Резервируем код из конкретного файла
                codeOpt = dataMatrixService.reserveNextCodeFromFile(userEmail, companyOwnerId, fileId);
                if (codeOpt.isEmpty()) {
                    log.warn("Нет доступных DataMatrix кодов в файле {} для продукта {}", fileId, productId);
                }
            } else {
                // Фоллбэк: резервируем из общего пула продукта
                codeOpt = dataMatrixService.reserveNextCodeForProduct(userEmail, companyOwnerId, productId);
                if (codeOpt.isEmpty()) {
                    log.warn("Нет доступных DataMatrix кодов для продукта {}", productId);
                }
            }

            if (codeOpt.isEmpty()) {
                // Рисуем заглушку - пустой квадрат с текстом "Нет кода"
                drawDataMatrixPlaceholder(canvas, x, y, width, height);
                return new int[]{0, 1};
            }

            String code = codeOpt.get();
            byte[] dmImage = generateDataMatrixImage(code, (int) width, (int) height);

            if (dmImage != null) {
                com.itextpdf.layout.element.Image img = new com.itextpdf.layout.element.Image(
                    com.itextpdf.io.image.ImageDataFactory.create(dmImage));
                img.setFixedPosition(x, y);
                img.scaleToFit(width, height);
                canvas.add(img);
                return new int[]{1, 0};
            }

            return new int[]{0, 0};

        } catch (Exception e) {
            log.error("Ошибка рендеринга DataMatrix: {}", e.getMessage(), e);
            return new int[]{0, 0};
        }
    }

    /**
     * Рисует заглушку вместо DataMatrix кода (пустой квадрат с текстом "Нет кода")
     */
    private void drawDataMatrixPlaceholder(Canvas canvas, float x, float y, float width, float height) {
        try {
            PdfCanvas pdfCanvas = new PdfCanvas(canvas.getPdfDocument().getLastPage());

            // Рисуем контур квадрата
            pdfCanvas.setStrokeColor(new DeviceRgb(200, 200, 200));
            pdfCanvas.setLineWidth(1);
            pdfCanvas.rectangle(x, y, width, height);
            pdfCanvas.stroke();

            // Добавляем текст "Нет кода" по центру
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            com.itextpdf.layout.element.Text text = new com.itextpdf.layout.element.Text("Нет кода")
                    .setFont(font)
                    .setFontSize(8)
                    .setFontColor(new DeviceRgb(150, 150, 150));

            Paragraph paragraph = new Paragraph(text)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);

            // Вычисляем позицию для центрирования текста
            float textX = x + width / 2;
            float textY = y + height / 2 - 4; // небольшая коррекция

            canvas.showTextAligned(paragraph, textX, textY, TextAlignment.CENTER);

        } catch (Exception e) {
            log.error("Ошибка рисования заглушки DataMatrix: {}", e.getMessage());
        }
    }

    private void renderQRCode(Canvas canvas, ElementDto element, float x, float y, float width, float height) {
        String content = element.getContent() != null ? element.getContent() : "";

        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 
                    (int) (width * 3), (int) (height * 3), hints);

            byte[] qrImage = bitMatrixToPng(bitMatrix);
            
            if (qrImage != null) {
                com.itextpdf.layout.element.Image img = new com.itextpdf.layout.element.Image(
                    com.itextpdf.io.image.ImageDataFactory.create(qrImage));
                img.setFixedPosition(x, y);
                img.scaleToFit(width, height);
                canvas.add(img);
            }
        } catch (WriterException e) {
            log.error("Ошибка генерации QR-кода: {}", e.getMessage());
        }
    }

    private void renderDate(Canvas canvas, ElementDto element, float x, float y, float width, float height, Integer rotation) {
        DateSettingsDto dateSettings = element.getDateSettings();

        // Fallback: если dateSettings == null, рендерим content как текст
        if (dateSettings == null) {
            renderText(canvas, element, x, y, width, height, rotation);
            return;
        }

        // Определяем дату изготовления
        LocalDate manufactureDate;
        Boolean smartDate = dateSettings.getSmartDate();
        String customDate = dateSettings.getCustomDate();

        if (smartDate != null && smartDate) {
            // Умная дата: дата изготовления = дата печати
            manufactureDate = LocalDate.now();
        } else if (customDate != null && !customDate.isEmpty()) {
            // Обычная дата: из customDate
            try {
                manufactureDate = LocalDate.parse(customDate);
            } catch (Exception e) {
                log.warn("Некорректная customDate: {}, используем текущую дату", customDate);
                manufactureDate = LocalDate.now();
            }
        } else {
            // Fallback: используем content как есть (уже отформатировано фронтендом)
            renderText(canvas, element, x, y, width, height, rotation);
            return;
        }

        // Расчёт даты «годен до» (только для умной даты)
        LocalDate bestBefore = null;
        if (smartDate != null && smartDate) {
            Integer shelfLifeValue = dateSettings.getShelfLifeValue();
            String shelfLifeUnit = dateSettings.getShelfLifeUnit();
            bestBefore = dateCalculator.calculateBestBefore(manufactureDate, shelfLifeValue, shelfLifeUnit);
        }

        // Получаем форматтер
        String format = dateSettings.getFormat() != null ? dateSettings.getFormat() : "DD.MM.YYYY";
        DateTimeFormatter formatter = dateCalculator.getFormatter(format);

        // Формируем текст по чекбоксам
        List<String> lines = new ArrayList<>();
        Boolean abbreviateText = dateSettings.getAbbreviateText();

        // Дата изготовления
        if (dateSettings.getShowManufactureDate() != null && dateSettings.getShowManufactureDate()) {
            String label = abbreviateText != null && abbreviateText ? "Дата изг.: " : "Дата изготовления: ";
            lines.add(label + manufactureDate.format(formatter));
        }

        // Годен до (только для умной даты)
        if (smartDate != null && smartDate && 
            dateSettings.getShowBestBefore() != null && dateSettings.getShowBestBefore() && 
            bestBefore != null) {
            String label = abbreviateText != null && abbreviateText ? "Годен до: " : "Годен до: ";
            lines.add(label + bestBefore.format(formatter));
        }

        // Срок годности
        if (dateSettings.getShowShelfLife() != null && dateSettings.getShowShelfLife()) {
            String label = abbreviateText != null && abbreviateText ? "Ср. годн.: " : "Срок годности: ";
            String shelfLifeFormatted = dateCalculator.formatShelfLife(
                dateSettings.getShelfLifeValue(), 
                dateSettings.getShelfLifeUnit()
            );
            lines.add(label + shelfLifeFormatted);
        }

        // Если ни один чекбокс не выбран, рендерим content как есть
        if (lines.isEmpty()) {
            renderText(canvas, element, x, y, width, height, rotation);
            return;
        }

        // Рендеринг
        if (lines.size() == 1) {
            // Одна строка — рендерим через renderText
            ElementDto textElement = ElementDto.builder()
                    .content(lines.get(0))
                    .style(element.getStyle())
                    .build();
            renderText(canvas, textElement, x, y, width, height, rotation);
        } else {
            // Многострочный рендеринг через PdfCanvas напрямую
            renderMultiLineText(canvas, element, lines, x, y, width, height, rotation);
        }
    }

    /**
     * Рендеринг многострочного текста для дат.
     * Каждая строка со смещением по Y = lineHeight (fontSize * 1.2).
     */
    private void renderMultiLineText(Canvas canvas, ElementDto element, List<String> lines,
                                      float x, float y, float width, float height, Integer rotation) {
        try {
            TextStyleDto style = element.getStyle();
            if (style == null) {
                style = TextStyleDto.builder().build();
            }

            PdfFont font = createFont(style);
            float fontSize = style.getFontSize() != null ? style.getFontSize().floatValue() : 12;
            float lineHeight = fontSize * 1.2f;

            PdfCanvas pdfCanvas = new PdfCanvas(canvas.getPdfDocument().getLastPage());

            float currentY = y + height - lineHeight; // начинаем сверху с отступом

            for (String line : lines) {
                if (currentY < y) {
                    break; // вышли за пределы элемента
                }

                // Выравнивание текста
                float textX = x;
                String textAlign = style.getTextAlign();
                if ("center".equals(textAlign)) {
                    textX = x + width / 2;
                } else if ("right".equals(textAlign)) {
                    textX = x + width;
                }

                // Рендеринг строки
                pdfCanvas.beginText();
                pdfCanvas.setFontAndSize(font, fontSize);
                pdfCanvas.setFillColor(parseColor(style.getColor() != null ? style.getColor() : "#000000"));
                
                if ("center".equals(textAlign)) {
                    float textWidth = font.getWidth(line, fontSize);
                    pdfCanvas.moveText(textX - (textWidth / 2), currentY);
                } else if ("right".equals(textAlign)) {
                    float textWidth = font.getWidth(line, fontSize);
                    pdfCanvas.moveText(textX - textWidth, currentY);
                } else {
                    pdfCanvas.moveText(textX, currentY);
                }
                
                pdfCanvas.showText(line);
                pdfCanvas.endText();

                currentY -= lineHeight;
            }

        } catch (Exception e) {
            log.error("Ошибка многострочного рендеринга даты: {}", e.getMessage());
            // Fallback: рендерим первую строку
            ElementDto textElement = ElementDto.builder()
                    .content(lines.get(0))
                    .style(element.getStyle())
                    .build();
            renderText(canvas, textElement, x, y, width, height, rotation);
        }
    }

    private void addSeparator(PdfDocument pdf, String separatorType) {
        PdfPage lastPage = pdf.getLastPage();
        if (lastPage == null) return;

        float width = lastPage.getPageSize().getWidth();
        float separatorHeight = 5;

        PdfCanvas pdfCanvas = new PdfCanvas(lastPage);
        
        Color separatorColor = "DARK".equals(separatorType) 
                ? new DeviceRgb(0, 0, 0) 
                : new DeviceRgb(240, 240, 240);
        
        pdfCanvas.setFillColor(separatorColor);
        pdfCanvas.rectangle(0, 0, width, separatorHeight);
        pdfCanvas.fill();
        pdfCanvas.release();
    }

    private LabelConfigDto parseConfig(String configJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(configJson, LabelConfigDto.class);
        } catch (Exception e) {
            throw new ValidationException("Ошибка парсинга конфигурации этикетки");
        }
    }

    private PdfFont createFont(TextStyleDto style) {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] generateDataMatrixImage(String code, int width, int height) throws WriterException {
        DataMatrixWriter writer = new DataMatrixWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix bitMatrix = writer.encode(code, BarcodeFormat.DATA_MATRIX, width, height, hints);
        return bitMatrixToPng(bitMatrix);
    }

    private byte[] bitMatrixToPng(BitMatrix bitMatrix) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(
                com.google.zxing.client.j2se.MatrixToImageWriter.toBufferedImage(bitMatrix),
                "PNG",
                baos
            );
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Ошибка конвертации BitMatrix в PNG: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Рендеринг изображения
     */
    private void renderImage(Canvas canvas, ElementDto element, float x, float y, float width, float height, Integer rotation) {
        String imageUrl = element.getImageUrl();

        if (imageUrl == null || imageUrl.isEmpty()) {
            log.warn("Пустой URL изображения");
            return;
        }

        try {
            // Загружаем изображение по URL
            java.net.URL url = new java.net.URL(imageUrl);
            byte[] imageBytes = url.openStream().readAllBytes();

            com.itextpdf.io.image.ImageData imageData = com.itextpdf.io.image.ImageDataFactory.create(imageBytes);
            com.itextpdf.layout.element.Image image = new com.itextpdf.layout.element.Image(imageData);

            image.setFixedPosition(x, y);
            image.scaleToFit(width, height);

            if (rotation != null && rotation != 0) {
                image.setRotationAngle(Math.toRadians(rotation));
            }

            canvas.add(image);
        } catch (java.net.MalformedURLException e) {
            log.error("Некорректный URL изображения: {}", imageUrl, e);
        } catch (java.io.IOException e) {
            log.error("Ошибка загрузки изображения по URL {}: {}", imageUrl, e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка рендеринга изображения: {}", e.getMessage(), e);
        }
    }

    /**
     * Рендеринг прямоугольника
     */
    private void renderRectangle(Canvas canvas, ElementDto element, float x, float y, float width, float height, Integer rotation) {
        try {
            PdfCanvas pdfCanvas = new PdfCanvas(canvas.getPdfDocument().getLastPage());

            // Цвет заливки
            String fillColor = element.getFillColor();
            if (fillColor != null && !fillColor.isEmpty()) {
                Color fill = parseColor(fillColor);
                pdfCanvas.setFillColor(fill);
            } else {
                pdfCanvas.setFillColor(new DeviceRgb(255, 255, 255)); // белый по умолчанию
            }

            // Цвет и толщина границы
            String borderColor = element.getBorderColor();
            Integer borderWidth = element.getBorderWidth();

            if (borderColor != null && !borderColor.isEmpty() && borderWidth != null && borderWidth > 0) {
                Color border = parseColor(borderColor);
                pdfCanvas.setStrokeColor(border);
                pdfCanvas.setLineWidth(borderWidth.floatValue());
            }

            // Применяем rotation если нужно
            if (rotation != null && rotation != 0) {
                double radians = Math.toRadians(rotation);
                float centerX = x + width / 2;
                float centerY = y + height / 2;

                pdfCanvas.saveState();
                pdfCanvas.concatMatrix(
                    (float) Math.cos(radians), (float) Math.sin(radians),
                    (float) -Math.sin(radians), (float) Math.cos(radians),
                    centerX - centerX * (float) Math.cos(radians) + centerY * (float) Math.sin(radians),
                    centerY - centerX * (float) Math.sin(radians) - centerY * (float) Math.cos(radians)
                );
            }

            // Рисуем прямоугольник
            pdfCanvas.rectangle(x, y, width, height);

            if (borderColor != null && !borderColor.isEmpty() && borderWidth != null && borderWidth > 0) {
                pdfCanvas.fillStroke(); // заливка + обводка
            } else {
                pdfCanvas.fill(); // только заливка
            }

            if (rotation != null && rotation != 0) {
                pdfCanvas.restoreState();
            }

        } catch (Exception e) {
            log.error("Ошибка рендеринга прямоугольника: {}", e.getMessage(), e);
        }
    }

    /**
     * Парсинг цвета из hex-строки (#RRGGBB или #RGB)
     */
    private Color parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) {
            return new DeviceRgb(0, 0, 0); // чёрный по умолчанию
        }

        try {
            // Убираем # если есть
            String hex = colorStr.startsWith("#") ? colorStr.substring(1) : colorStr;

            // Поддержка короткой формы #RGB
            if (hex.length() == 3) {
                hex = String.valueOf(hex.charAt(0)) + hex.charAt(0) +
                      hex.charAt(1) + hex.charAt(1) +
                      hex.charAt(2) + hex.charAt(2);
            }

            if (hex.length() != 6) {
                log.warn("Некорректный формат цвета: {}", colorStr);
                return new DeviceRgb(0, 0, 0);
            }

            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);

            return new DeviceRgb(r, g, b);
        } catch (Exception e) {
            log.error("Ошибка парсинга цвета {}: {}", colorStr, e.getMessage());
            return new DeviceRgb(0, 0, 0);
        }
    }
}
