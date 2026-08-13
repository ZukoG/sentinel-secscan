package com.sentinel.secscan.report;

import com.sentinel.secscan.domain.Finding;
import com.sentinel.secscan.domain.Scan;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the PDF directly with Apache PDFBox rather than an HTML/CSS
 * templating library. The report's layout is simple enough (a title
 * block plus a list of findings) that manual placement is less overhead
 * than pulling in a templating engine just for this.
 */
@Component
public class PdfReportGenerator {

    private static final PDFont TITLE_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont HEADING_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont BODY_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont ITALIC_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    public byte[] generate(Scan scan, List<Finding> findings) {
        try (PDDocument document = new PDDocument()) {
            Cursor cursor = new Cursor(document);

            cursor.writeLine("Sentinel Security Report", TITLE_FONT, 18);
            cursor.skipLine();
            cursor.writeLine("Website: " + scan.getWebsite().getUrl(), HEADING_FONT, 12);
            cursor.writeLine("Scanned: " + TIMESTAMP_FORMAT.format(scan.getCompletedAt()), BODY_FONT, 11);
            cursor.writeLine("Overall score: " + scan.getOverallScore() + " / 100", HEADING_FONT, 13);
            cursor.writeLine("Risk rating: " + scan.getRiskRating(), HEADING_FONT, 13);
            cursor.skipLine();

            cursor.writeLine("Findings (" + findings.size() + ")", HEADING_FONT, 13);
            cursor.skipLine();

            for (Finding finding : findings) {
                cursor.writeFinding(finding);
            }

            cursor.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate PDF report", e);
        }
    }

    /**
     * Tracks the current page/content stream/vertical position and adds
     * a new page automatically once content would run past the bottom
     * margin. PDFBox ties a content stream to exactly one page, so this
     * bit of bookkeeping is the price of a low-level PDF library, an
     * HTML-to-PDF renderer would have handled pagination for free.
     */
    private static final class Cursor {
        private static final float MARGIN = 50f;
        private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
        private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
        private static final float LINE_HEIGHT = 16f;

        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;

        Cursor(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        void writeFinding(Finding finding) throws IOException {
            writeLine("[" + finding.getSeverity() + "] " + finding.getCheckName(), HEADING_FONT, 12);
            writeWrapped(finding.getDescription(), BODY_FONT, 10);
            writeWrapped("Recommendation: " + finding.getRecommendation(), ITALIC_FONT, 10);
            skipLine();
        }

        void skipLine() {
            y -= LINE_HEIGHT / 2;
        }

        void writeWrapped(String text, PDFont font, float fontSize) throws IOException {
            for (String line : wrap(text, font, fontSize, PAGE_WIDTH - 2 * MARGIN)) {
                writeLine(line, font, fontSize);
            }
        }

        void writeLine(String text, PDFont font, float fontSize) throws IOException {
            ensureSpace();
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(text);
            stream.endText();
            y -= LINE_HEIGHT;
        }

        private void ensureSpace() throws IOException {
            if (y < MARGIN) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String paragraph : text.split("\n")) {
                StringBuilder current = new StringBuilder();
                for (String word : paragraph.split(" ")) {
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if (font.getStringWidth(candidate) / 1000 * fontSize > maxWidth && !current.isEmpty()) {
                        lines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        current = new StringBuilder(candidate);
                    }
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
            }
            return lines;
        }

        void close() throws IOException {
            stream.close();
        }
    }
}
