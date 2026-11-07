package com.knowflow.ai.ingestion;

import com.knowflow.ai.common.exception.KnowFlowException;
import com.knowflow.ai.document.FileType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);
    private final Tika tika = new Tika();

    public List<ExtractedPage> extractContent(File file, FileType fileType) {
        log.info("Extracting content from file: {} of type: {}", file.getName(), fileType);
        try {
            switch (fileType) {
                case PDF:
                    return extractPdf(file);
                case DOCX:
                    return extractDocx(file);
                case PPTX:
                    return extractPptx(file);
                case CSV:
                case TXT:
                    return extractPlainText(file);
                default:
                    return extractWithTika(file);
            }
        } catch (Exception e) {
            log.error("Failed to extract content from {}: {}", file.getName(), e.getMessage());
            throw new KnowFlowException("Text extraction failed: " + e.getMessage(), e);
        }
    }

    private List<ExtractedPage> extractPdf(File file) throws Exception {
        List<ExtractedPage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file)) {
            int numberOfPages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 1; i <= numberOfPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);

                String heading = extractProbableHeading(pageText, "Page " + i);
                pages.add(new ExtractedPage(i, pageText, heading));
            }
        }
        return pages.isEmpty() ? extractWithTika(file) : pages;
    }

    private List<ExtractedPage> extractDocx(File file) throws Exception {
        List<ExtractedPage> pages = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument docx = new XWPFDocument(fis)) {

            StringBuilder sb = new StringBuilder();
            String currentHeading = "Overview";
            int virtualPage = 1;

            for (XWPFParagraph paragraph : docx.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }

                // Check if heading style
                String style = paragraph.getStyle();
                if (style != null && (style.toLowerCase().contains("heading") || style.toLowerCase().contains("title"))) {
                    currentHeading = text.trim();
                }

                sb.append(text).append("\n");

                // Approximate 2500 characters per virtual page
                if (sb.length() >= 2500) {
                    pages.add(new ExtractedPage(virtualPage++, sb.toString(), currentHeading));
                    sb.setLength(0);
                }
            }

            if (sb.length() > 0 || pages.isEmpty()) {
                pages.add(new ExtractedPage(virtualPage, sb.toString(), currentHeading));
            }
        }
        return pages.isEmpty() ? extractWithTika(file) : pages;
    }

    private List<ExtractedPage> extractPptx(File file) throws Exception {
        List<ExtractedPage> pages = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XMLSlideShow pptx = new XMLSlideShow(fis)) {

            int slideIndex = 1;
            for (XSLFSlide slide : pptx.getSlides()) {
                StringBuilder slideText = new StringBuilder();
                String slideTitle = slide.getTitle() != null ? slide.getTitle() : "Slide " + slideIndex;

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            slideText.append(text).append("\n");
                        }
                    }
                }

                pages.add(new ExtractedPage(slideIndex, slideText.toString(), slideTitle));
                slideIndex++;
            }
        }
        return pages.isEmpty() ? extractWithTika(file) : pages;
    }

    private List<ExtractedPage> extractPlainText(File file) throws Exception {
        List<ExtractedPage> pages = new ArrayList<>();
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        // Break into pages of ~2500 characters
        int pageSize = 2500;
        int pageNum = 1;
        int length = content.length();

        for (int i = 0; i < length; i += pageSize) {
            int end = Math.min(length, i + pageSize);
            String chunk = content.substring(i, end);
            pages.add(new ExtractedPage(pageNum++, chunk, "Section " + (pageNum - 1)));
        }

        if (pages.isEmpty()) {
            pages.add(new ExtractedPage(1, content, "Full Content"));
        }
        return pages;
    }

    private List<ExtractedPage> extractWithTika(File file) throws Exception {
        List<ExtractedPage> pages = new ArrayList<>();
        try (InputStream is = new FileInputStream(file)) {
            String extracted = tika.parseToString(is);
            pages.add(new ExtractedPage(1, extracted, "General"));
        }
        return pages;
    }

    private String extractProbableHeading(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() >= 3 && trimmed.length() <= 80 && !trimmed.matches("^[0-9]+$")) {
                return trimmed;
            }
        }
        return fallback;
    }

    public String detectMimeType(File file) {
        try {
            return tika.detect(file);
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }
}
