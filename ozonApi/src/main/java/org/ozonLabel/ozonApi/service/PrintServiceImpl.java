package org.ozonLabel.ozonApi.service;

import com.itextpdf.barcodes.Barcode128;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
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

    // Константы для конвертации мм в пункты (1 мм ≈ 2.835 pt при 72 dpi)
    private static final double MM_TO_POINTS = 2.83464567;

    @Override
    @Transactional(readOnly = true)
    public byte[] generateLabelsPdf(String userEmail, Long companyOwnerId, PrintRequest request) {
        companyService.checkAccess(userEmail, companyOwnerId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);

            List<Long> productIds = request.getProductIds();
            Map<Long, Integer> copies = request.getCopies() != null ? request.getCopies() : new HashMap<>();
            String separatorType = request.getSeparatorType() != null ? request.getSeparatorType() : "NONE";

            boolean firstLabel = true;
            Long lastProductId = null;

            for (Long productId : productIds) {
                LabelResponseDto label;
                try {
                    label = labelService.getLabelByProductId(userEmail, companyOwnerId, productId);
                } catch (Exception e) {
                    log.warn("Этикетка для продукта {} не найдена, пропускаем", productId);
                    continue;
                }

                int copiesCount = copies.getOrDefault(productId, 1);

                for (int i = 0; i < copiesCount; i++) {
                    if (!firstLabel && !separatorType.equals("NONE") && !productId.equals(lastProductId)) {
                        addSeparator(pdf, separatorType);
                    }

                    generateLabelPage(pdf, label, companyOwnerId, productId);
                    firstLabel = false;
                }

                lastProductId = productId;
            }

            pdf.close();
            log.info("Сгенерирован PDF с этикетками для {} продуктов", productIds.size());
            return baos.toByteArray();
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

    private void generateLabelPage(PdfDocument pdf, LabelResponseDto label, Long companyOwnerId, Long productId) {
        float widthPt = label.getWidth().floatValue() * (float) MM_TO_POINTS;
        float heightPt = label.getHeight().floatValue() * (float) MM_TO_POINTS;

        PdfPage page = pdf.addNewPage(new PageSize(widthPt, heightPt));
        PdfCanvas pdfCanvas = new PdfCanvas(page);
        Canvas canvas = new Canvas(pdfCanvas, page.getPageSize());

        try {
            LabelConfigDto config = label.getConfig();

            List<ElementDto> elements = new ArrayList<>(config.getElements());
            elements.sort(Comparator.comparingInt(e -> e.getZIndex() != null ? e.getZIndex() : 0));

            for (ElementDto element : elements) {
                if (element.getVisible() != null && !element.getVisible()) {
                    continue;
                }
                renderElement(canvas, element, config, companyOwnerId, productId);
            }

        } catch (Exception e) {
            log.error("Ошибка рендеринга этикетки: {}", e.getMessage(), e);
        } finally {
            canvas.close();
        }
    }

    private void renderElement(Canvas canvas, ElementDto element, LabelConfigDto config, Long companyOwnerId, Long productId) {
        String type = element.getType();

        float x = element.getX().floatValue() * (float) MM_TO_POINTS;
        float y = element.getY().floatValue() * (float) MM_TO_POINTS;
        float width = element.getWidth().floatValue() * (float) MM_TO_POINTS;
        float height = element.getHeight().floatValue() * (float) MM_TO_POINTS;

        switch (type) {
            case "text":
                renderText(canvas, element, x, y, width, height);
                break;
            case "barcode":
                renderBarcode(canvas, element, x, y, width, height);
                break;
            case "datamatrix":
                renderDataMatrix(canvas, element, x, y, width, height, companyOwnerId, productId);
                break;
            case "qrcode":
                renderQRCode(canvas, element, x, y, width, height);
                break;
            case "date":
                renderDate(canvas, element, x, y, width, height);
                break;
        }
    }

    private void renderText(Canvas canvas, ElementDto element, float x, float y, float width, float height) {
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
                    .setFontSize(fontSize)
                    .setFixedPosition(x, y, width);

            if (style.getTextAlign() != null) {
                switch (style.getTextAlign()) {
                    case "left": paragraph.setTextAlignment(TextAlignment.LEFT); break;
                    case "center": paragraph.setTextAlignment(TextAlignment.CENTER); break;
                    case "right": paragraph.setTextAlignment(TextAlignment.RIGHT); break;
                }
            }

            canvas.add(paragraph);
        } catch (Exception e) {
            log.error("Ошибка рендеринга текста: {}", e.getMessage());
        }
    }

    private void renderBarcode(Canvas canvas, ElementDto element, float x, float y, float width, float height) {
        // Упрощённая заглушка для штрихкода - текст с кодом
        // В полной реализации здесь должна быть генерация штрихкода iText
        String content = element.getContent() != null ? element.getContent() : "";
        
        try {
            Paragraph paragraph = new Paragraph(content)
                    .setFontSize(8)
                    .setFixedPosition(x, y, width);
            canvas.add(paragraph);
        } catch (Exception e) {
            log.error("Ошибка рендеринга штрихкода: {}", e.getMessage());
        }
    }

    private void renderDataMatrix(Canvas canvas, ElementDto element, float x, float y, float width, float height, Long companyOwnerId, Long productId) {
        try {
            Optional<String> codeOpt = dataMatrixService.reserveNextCodeForProduct("system", companyOwnerId, productId);
            
            if (codeOpt.isEmpty()) {
                throw new ValidationException("Недостаточно кодов ЧЗ для продукта " + productId);
            }

            String code = codeOpt.get();
            byte[] dmImage = generateDataMatrixImage(code, (int) width, (int) height);
            
            if (dmImage != null) {
                com.itextpdf.layout.element.Image img = new com.itextpdf.layout.element.Image(
                    com.itextpdf.io.image.ImageDataFactory.create(dmImage));
                img.setFixedPosition(x, y);
                img.scaleToFit(width, height);
                canvas.add(img);
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка рендеринга DataMatrix: {}", e.getMessage());
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

    private void renderDate(Canvas canvas, ElementDto element, float x, float y, float width, float height) {
        DateSettingsDto dateSettings = element.getDateSettings();
        String content = element.getContent();

        if (dateSettings != null && dateSettings.getUseCurrentDate() != null && dateSettings.getUseCurrentDate()) {
            LocalDate now = LocalDate.now();
            String format = dateSettings.getFormat() != null ? dateSettings.getFormat() : "DD.MM.YYYY";
            
            DateTimeFormatter formatter = switch (format) {
                case "DD.MM.YYYY" -> DateTimeFormatter.ofPattern("dd.MM.yyyy");
                case "DD.MM.GG" -> DateTimeFormatter.ofPattern("dd.MM.yy");
                case "YYYY-MM-DD" -> DateTimeFormatter.ofPattern("yyyy-MM-dd");
                default -> DateTimeFormatter.ofPattern("dd.MM.yyyy");
            };

            content = now.format(formatter);
        }

        ElementDto textElement = ElementDto.builder()
                .content(content)
                .style(element.getStyle())
                .build();
        
        renderText(canvas, textElement, x, y, width, height);
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
}
