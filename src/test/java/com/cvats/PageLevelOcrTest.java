package com.cvats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.sourceforge.tess4j.TesseractException;

/**
 * CR-02: Page-level OCR regresyon testleri.
 *
 * <p>Gerçek Tesseract veya Docker gerektirmez; Linux subprocess
 * {@code runTesseract} metodu sahte komut ile test edilir.
 * Windows Tess4J yolu da mocklanabilir ancak bu testler
 * platform-bağmsız {@code runTesseract} yardımcı metotunu kullanır.</p>
 */
public class PageLevelOcrTest {

    @TempDir
    Path tempDir;

    private PDFont unicodeFont;

    private PDFont getUnicodeFont(PDDocument doc) throws IOException {
        if (unicodeFont != null) {
            return unicodeFont;
        }
        // Try to load a Unicode-compatible TTF font from common system locations
        String[] fontPaths = {
            // Windows
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/arialuni.ttf",
            "C:/Windows/Fonts/tahoma.ttf",
            // Linux
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
            "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
            // macOS
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/Library/Fonts/Arial Unicode.ttf"
        };
        for (String path : fontPaths) {
            File fontFile = new File(path);
            if (fontFile.exists()) {
                unicodeFont = PDType0Font.load(doc, fontFile);
                return unicodeFont;
            }
        }
        // Fallback: try to load from classpath if bundled
        try (InputStream is = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (is != null) {
                unicodeFont = PDType0Font.load(doc, is);
                return unicodeFont;
            }
        }
        // Last resort: use built-in Helvetica (won't support Turkish chars but test can use ASCII)
        unicodeFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        return unicodeFont;
    }

    private PDDocument createTextPageDocument(String text) throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try (var contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
            contentStream.beginText();
            contentStream.setFont(getUnicodeFont(doc), 12);
            contentStream.newLineAtOffset(50, 700);
            writeMultilineText(contentStream, text);
            contentStream.endText();
        }
        return doc;
    }

    private void writeMultilineText(org.apache.pdfbox.pdmodel.PDPageContentStream cs, String text) throws IOException {
        String[] lines = text.split("\\R");
        float leading = 14f; // line spacing
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                cs.newLineAtOffset(0, -leading);
            }
            cs.showText(lines[i]);
        }
    }

    private PDDocument createBlankPageDocument() throws IOException {
        PDDocument doc = new PDDocument();
        doc.addPage(new PDPage(PDRectangle.A4));
        return doc;
    }

    private PDDocument createMixedDocument(String textPageContent) throws IOException {
        PDDocument doc = new PDDocument();

        // Page 0: text page
        PDPage textPage = new PDPage(PDRectangle.A4);
        doc.addPage(textPage);
        try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, textPage)) {
            cs.beginText();
            cs.setFont(getUnicodeFont(doc), 12);
            cs.newLineAtOffset(50, 700);
            writeMultilineText(cs, textPageContent);
            cs.endText();
        }

        // Page 1: blank page (will trigger OCR)
        doc.addPage(new PDPage(PDRectangle.A4));

        return doc;
    }

    private PDDocument createMeaninglessTextPageDocument() throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try (var contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
            contentStream.beginText();
            contentStream.setFont(getUnicodeFont(doc), 12);
            contentStream.newLineAtOffset(50, 700);
            contentStream.showText("... ... ... . . .");
            contentStream.endText();
        }
        return doc;
    }

    private PDDocument createMultiPageTextDocument(List<String> pageContents) throws IOException {
        PDDocument doc = new PDDocument();
        for (String content : pageContents) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(getUnicodeFont(doc), 12);
                cs.newLineAtOffset(50, 700);
                writeMultilineText(cs, content);
                cs.endText();
            }
        }
        return doc;
    }

    @Test
    void isTextUsableBosMetinFalseDonmeli() {
        assertFalse(OCRProcessor.isTextUsable(""));
        assertFalse(OCRProcessor.isTextUsable("   "));
        assertFalse(OCRProcessor.isTextUsable("\n\t\r"));
    }

    @Test
    void isTextUsableCokKisaMetinFalseDonmeli() {
        assertFalse(OCRProcessor.isTextUsable("Hi"));
        assertFalse(OCRProcessor.isTextUsable("Short"));
        assertFalse(OCRProcessor.isTextUsable("12345"));
    }

    @Test
    void isTextUsableAnlamsizKarakterFalseDonmeli() {
        assertFalse(OCRProcessor.isTextUsable("... ... ... . . ."));
        assertFalse(OCRProcessor.isTextUsable("--- --- ---"));
        assertFalse(OCRProcessor.isTextUsable("/// \\\\\\ ///"));
    }

    @Test
    void isTextUsableNormalMetinTrueDonmeli() {
        assertTrue(OCRProcessor.isTextUsable("Bu geçerli bir metin örneğidir."));
        assertTrue(OCRProcessor.isTextUsable("Email: test@example.com Telefon: 0555 123 45 67"));
        assertTrue(OCRProcessor.isTextUsable("Java Developer with 5 years experience."));
    }

    @Test
    void tamMetinliPdfOcrKullanmamali() throws IOException, TesseractException {
        String content = "E-posta: test@example.com\nTelefon: 0555 123 45 67\n\nEğitim\nBilgisayar Mühendisliği\n\nDeneyim\nJava Developer";
        try (PDDocument doc = createTextPageDocument(content)) {
            List<OCRProcessor.PageResult> results = OCRProcessor.extractTextPerPage(doc);

            assertEquals(1, results.size());
            assertFalse(results.get(0).ocrUsed(), "Metinli sayfa OCR kullanmamalı");
            assertTrue(results.get(0).text().contains("test@example.com"));
            assertTrue(results.get(0).text().contains("Java Developer"));
        }
    }

    @Test
    void tamTaranmisPdfOcrKullanmali() throws IOException, TesseractException {
        try (PDDocument doc = createBlankPageDocument()) {
            Function<BufferedImage, String> fakeOcr = img -> "OCR sonucu: taranmış sayfa metni";

            List<OCRProcessor.PageResult> results = OCRProcessor.extractTextPerPage(doc, fakeOcr);

            assertEquals(1, results.size());
            assertTrue(results.get(0).ocrUsed(), "Boş sayfa OCR kullanmalı");
            assertTrue(results.get(0).text().contains("OCR sonucu"));
        }
    }

    @Test
    void karisikPdfDogruSayfaSirasiVeOcrRaporlamali() throws IOException, TesseractException {
        String textContent = "E-posta: test@example.com\nDeneyim\nJava Developer";
        try (PDDocument doc = createMixedDocument(textContent)) {
            Function<BufferedImage, String> fakeOcr = img -> "Sayfa 2 OCR metni";

            List<OCRProcessor.PageResult> results = OCRProcessor.extractTextPerPage(doc, fakeOcr);

            assertEquals(2, results.size());

            // Page 0: text page, no OCR
            assertEquals(0, results.get(0).pageIndex());
            assertFalse(results.get(0).ocrUsed());
            assertTrue(results.get(0).text().contains("test@example.com"));

            // Page 1: blank page, OCR used
            assertEquals(1, results.get(1).pageIndex());
            assertTrue(results.get(1).ocrUsed());
            assertTrue(results.get(1).text().contains("Sayfa 2 OCR metni"));
        }
    }

    @Test
    void anlamsizMetinKatmanliSayfaOcrKullanmali() throws IOException, TesseractException {
        try (PDDocument doc = createMeaninglessTextPageDocument()) {
            Function<BufferedImage, String> fakeOcr = img -> "Anlamsiz sayfa OCR sonucu";

            List<OCRProcessor.PageResult> results = OCRProcessor.extractTextPerPage(doc, fakeOcr);

            assertEquals(1, results.size());
            assertTrue(results.get(0).ocrUsed(), "Anlamsiz metin katmanı OCR tetiklemeli");
            assertTrue(results.get(0).text().contains("Anlamsiz sayfa OCR sonucu"));
        }
    }

    @Test
    void extractTextPerPageSayfaSirasiKorunmali() throws IOException, TesseractException {
        String page1 = "Sayfa 1 icerigi";
        String page2 = "Sayfa 2 icerigi";
        String page3 = "Sayfa 3 icerigi";

        try (PDDocument doc = createMultiPageTextDocument(List.of(page1, page2, page3))) {
            List<OCRProcessor.PageResult> results = OCRProcessor.extractTextPerPage(doc);

            assertEquals(3, results.size());
            assertEquals(0, results.get(0).pageIndex());
            assertEquals(1, results.get(1).pageIndex());
            assertEquals(2, results.get(2).pageIndex());
            assertTrue(results.get(0).text().contains("Sayfa 1"));
            assertTrue(results.get(1).text().contains("Sayfa 2"));
            assertTrue(results.get(2).text().contains("Sayfa 3"));
        }
    }

    @Test
    void ocrBasarisizligiSessizceBasariliSayilmaz() throws IOException {
        try (PDDocument doc = createBlankPageDocument()) {
            List<String> failingCmd = List.of(
                    "cmd.exe", "/c", "exit 1"
            );

            IOException ex = assertThrows(IOException.class, () -> {
                PDFRenderer renderer = new PDFRenderer(doc);
                BufferedImage image = renderer.renderImageWithDPI(0, 72);
                OCRProcessor.runTesseract(image, 60, TimeUnit.SECONDS, failingCmd);
            });

            assertTrue(ex.getMessage().contains("Cikis kodu: 1"),
                    "OCR hatası exception olarak fırlatılmalı, sessizce yutulmamalı");
        }
    }

@Test
    void ocrFonksiyonuCagrilmaliSadeceGerekliSayfalarda() throws IOException, TesseractException {
        // 3 sayfa: metinli, boş, metinli
        try (PDDocument doc = createMultiPageTextDocument(List.of(
                "Sayfa 1 metinli icerik",
                "", // boş sayfa - PDFBox boş metin döndürür
                "Sayfa 3 metinli icerik"
        ))) {
            int[] ocrCallCount = {0};
            Function<BufferedImage, String> fakeOcr = img -> {
                ocrCallCount[0]++;
                return "OCR sayfa " + ocrCallCount[0];
            };

            List<OCRProcessor.PageResult> results = OCRProcessor.extractTextPerPage(doc, fakeOcr);

            // Sayfa 0: metinli - OCR çağrılmamalı (ocrUsed=false)
            // Sayfa 1: boş - OCR çağrılmalı (ocrUsed=true)
            // Sayfa 2: metinli - OCR çağrılmamalı (ocrUsed=false)
            assertEquals(1, ocrCallCount[0], "OCR fonksiyonu sadece boş sayfa için bir kez çağrılmalı");
            assertEquals(3, results.size());
            assertFalse(results.get(0).ocrUsed(), "Sayfa 0 metinli, OCR kullanılmamalı");
            assertTrue(results.get(1).ocrUsed(), "Sayfa 1 boş, OCR kullanılmalı");
            assertFalse(results.get(2).ocrUsed(), "Sayfa 2 metinli, OCR kullanılmamalı");
            assertEquals(1, results.get(1).pageIndex());
        }
    }

    @Test
    void extractTextSayfaAralariYeniSatirIleBirlesmeli() throws IOException, TesseractException {
        // Her sayfa en az 20 karakter ve yeterli alfanümerik oranda olmalı (isTextUsable için)
        String page1 = "Sayfa 1 son kelime icerigi";
        String page2 = "Sayfa 2 baslangic kelime icerigi";

        try (PDDocument doc = createMultiPageTextDocument(List.of(page1, page2))) {
            String combined = OCRProcessor.extractText(doc);
            // Sayfalar arası en az bir yeni satır olmalı (PDFBox trailing newline ekleyebilir)
            String pattern = java.util.regex.Pattern.quote(page1) + "\\R+" + java.util.regex.Pattern.quote(page2);
            assertTrue(combined.matches("(?s).*" + pattern + ".*"),
                    "Sayfalar arası yeni satır karakteri(ler) olmalı");
            // Kelimeler sayfa sınırında birleşmemeli
            assertFalse(combined.contains("icerigi" + page2.substring(0, 5)),
                    "Kelimeler sayfa sınırında birleşmemeli");
        }
    }

    @Test
    void extractTextFromPageSadeceIstenenSayfayiIslemeli() throws IOException, TesseractException {
        // Her sayfa en az 20 karakter ve yeterli alfanümerik oranda olmalı
        try (PDDocument doc = createMultiPageTextDocument(List.of(
                "Sayfa 0 icerik metni burada",
                "Sayfa 1 icerik metni burada",
                "Sayfa 2 icerik metni burada"
        ))) {
            int[] ocrCallCount = {0};
            Function<BufferedImage, String> fakeOcr = img -> {
                ocrCallCount[0]++;
                return "OCR-" + ocrCallCount[0];
            };

            // Sadece sayfa 1 (index 1) isteniyor
            String page1Text = OCRProcessor.extractTextFromPage(doc, 1, fakeOcr);

            // PDFTextStripper trailing newline ekleyebilir, trim ile karşılaştır
            assertEquals("Sayfa 1 icerik metni burada", page1Text.trim());
            assertEquals(0, ocrCallCount[0], "Metinli sayfa için OCR çağrılmamalı");
        }
    }

    @Test
    void extractTextFromPageBosSayfaIcinOcrCagirmali() throws IOException, TesseractException {
        try (PDDocument doc = createMultiPageTextDocument(List.of(
                "Sayfa 0 icerik",
                "", // boş sayfa
                "Sayfa 2 icerik"
        ))) {
            int[] ocrCallCount = {0};
            Function<BufferedImage, String> fakeOcr = img -> {
                ocrCallCount[0]++;
                return "OCR-sayfa1";
            };

            // Sadece sayfa 1 (index 1, boş) isteniyor
            String page1Text = OCRProcessor.extractTextFromPage(doc, 1, fakeOcr);

            assertEquals("OCR-sayfa1", page1Text);
            assertEquals(1, ocrCallCount[0], "Boş sayfa için OCR bir kez çağrılmalı");
        }
    }

    @Test
    void extractTextFromPageGecersizIndexAtmali() throws IOException {
        try (PDDocument doc = createMultiPageTextDocument(List.of("Sayfa 0"))) {
            IndexOutOfBoundsException ex = assertThrows(IndexOutOfBoundsException.class,
                    () -> OCRProcessor.extractTextFromPage(doc, -1));
            assertTrue(ex.getMessage().contains("out of bounds"));

            ex = assertThrows(IndexOutOfBoundsException.class,
                    () -> OCRProcessor.extractTextFromPage(doc, 1));
            assertTrue(ex.getMessage().contains("out of bounds"));

            ex = assertThrows(IndexOutOfBoundsException.class,
                    () -> OCRProcessor.extractTextFromPage(doc, 5));
            assertTrue(ex.getMessage().contains("out of bounds"));
        }
    }
}
