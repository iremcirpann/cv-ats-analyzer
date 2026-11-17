package com.cvats;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.function.Function;

public class OCRProcessor {

    private static final int MIN_USABLE_TEXT_LENGTH = 20;
    private static final double MIN_ALPHANUMERIC_RATIO = 0.3;
    private static final int MAX_PAGE_COUNT = 50;
    private static final int MAX_IMAGE_PIXELS = 20_000_000;
    private static final int RENDER_DPI = 300;
    private static final double POINTS_PER_INCH = 72.0;
    private static final long MAX_TOTAL_SECONDS = 120;

    /**
     * Varsayılan OCR dili. Ortam değişkeni {@code OCR_LANG} ile ezilebilir.
     * Boş veya geçersiz değerlerde bu varsayılan kullanılır.
     */
    private static final String DEFAULT_OCR_LANGUAGE = "tur+eng";

    /**
     * Desteklenen dil kodları (Tesseract formatında).
     */
    private static final java.util.Set<String> SUPPORTED_LANGUAGES =
            java.util.Set.of("tur", "eng", "tur+eng", "eng+tur");

    /**
     * Geçerli bir OCR dil dizesi döndürür.
     * {@code OCR_LANG} ortam değişkeni tanımlı ve desteklenen bir dil ise onu kullanır;
     * aksi halde {@link #DEFAULT_OCR_LANGUAGE} değerini döndürür.
     *
     * @return Geçerli dil dizesi (örneğin "tur+eng")
     */
    private static String resolveOcrLanguage() {
        return resolveOcrLanguage(System.getenv("OCR_LANG"));
    }

    /**
     * Belirtilen ortam değişkeni değerine göre OCR dilini çözümler.
     *
     * @param envValue OCR_LANG ortam değişkeninin değeri (null olabilir)
     * @return Geçerli dil dizesi (örneğin "tur+eng")
     */
    static String resolveOcrLanguage(String envValue) {
        if (envValue != null && !envValue.isBlank()) {
            String normalized = envValue.trim().toLowerCase(Locale.ROOT);
            if (SUPPORTED_LANGUAGES.contains(normalized)) {
                return normalized;
            }
        }
        return DEFAULT_OCR_LANGUAGE;
    }

    /**
     * Varsayılan Tesseract komutunu oluşturur.
     * Dil bilgisi {@link #resolveOcrLanguage()} ile belirlenir.
     *
     * @return Tesseract komut listesi
     */
    static List<String> defaultTesseractCommand() {
        List<String> cmd = new ArrayList<>(6);
        cmd.add("tesseract");
        cmd.add("%IMAGE%");
        cmd.add("stdout");
        cmd.add("-l");
        cmd.add(resolveOcrLanguage());
        cmd.add("--psm");
        cmd.add("3");
        cmd.add("--dpi");
        cmd.add("300");
        return cmd;
    }

    public record PageResult(String text, boolean ocrUsed, int pageIndex,
                             boolean ocrFailed, String errorMessage) {

        public PageResult(String text, boolean ocrUsed, int pageIndex) {
            this(text, ocrUsed, pageIndex, false, null);
        }
    }

    public static List<PageResult> extractTextPerPage(PDDocument document)
            throws IOException, TesseractException, InterruptedException {
        return extractTextPerPage(document, null, Long.MAX_VALUE);
    }

    public static List<PageResult> extractTextPerPage(PDDocument document, Function<BufferedImage, String> ocrFunction)
            throws IOException, TesseractException, InterruptedException {
        return extractTextPerPage(document, ocrFunction, Long.MAX_VALUE);
    }

    public static List<PageResult> extractTextPerPage(PDDocument document, Function<BufferedImage, String> ocrFunction,
                                                       long deadlineNanos)
            throws IOException, TesseractException, InterruptedException {

        checkDeadline(deadlineNanos);

        int numPages = document.getNumberOfPages();
        if (numPages > MAX_PAGE_COUNT) {
            throw new IOException(
                    "PDF dosyasi 50 sayfa sinirini asti: " + numPages + " sayfa."
            );
        }

        PDFRenderer renderer = new PDFRenderer(document);
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);

        boolean windows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("win");

        Tesseract tesseract = null;

        if (windows && ocrFunction == null) {
            tesseract = new Tesseract();
            tesseract.setDatapath(
                    "C:\\Program Files\\Tesseract-OCR\\tessdata"
            );
            tesseract.setLanguage(resolveOcrLanguage());
            tesseract.setPageSegMode(3);
            tesseract.setVariable("user_defined_dpi", "300");
        }

        List<PageResult> results = new ArrayList<>();

        for (int page = 0; page < document.getNumberOfPages(); page++) {
            checkDeadline(deadlineNanos);

            String pageText;
            boolean useOcr;
            try {
                stripper.setStartPage(page + 1);
                stripper.setEndPage(page + 1);
                pageText = stripper.getText(document);
                useOcr = !isTextUsable(pageText);
            } catch (IOException e) {
                results.add(new PageResult("", false, page, true, e.getMessage()));
                continue;
            }

            checkDeadline(deadlineNanos);

            String finalText;
            if (useOcr) {
                BufferedImage image;
                try {
                    PDPage pdfPage = document.getPage(page);
                    if (!areImageDimensionsSafe(pdfPage, RENDER_DPI)) {
                        throw new IOException(
                                "OCR goruntusu piksel sinirini asti: en fazla " + MAX_IMAGE_PIXELS + " piksel izlenir."
                        );
                    }
                    image = renderer.renderImageWithDPI(page, RENDER_DPI);
                } catch (IOException e) {
                    results.add(new PageResult("", true, page, true, e.getMessage()));
                    continue;
                }

                checkDeadline(deadlineNanos);
                try {
                    if (ocrFunction != null) {
                        finalText = ocrFunction.apply(image);
                    } else if (windows) {
                        // Windows Tess4J doOCR() native cagrisi bloklayan, timeout almayan ve
                        // icinde iptal edilemeyen bir islemdir. Sure kontrolu once ve sonra
                        // yapilir ancak cagri icinde kesilemez (best-effort).
                        checkDeadline(deadlineNanos);
                        finalText = tesseract.doOCR(image);
                        checkDeadline(deadlineNanos);
                    } else {
                        long remainingNanos = deadlineNanos - System.nanoTime();
                        if (remainingNanos <= 0) {
                            throw new IOException(
                                    "Toplam islem butcesi (120 saniye) doldu. Analiz durduruldu."
                            );
                        }
                        long subprocessTimeoutNanos =
                                Math.min(remainingNanos, TimeUnit.SECONDS.toNanos(60));
                        finalText = runTesseract(image, subprocessTimeoutNanos, TimeUnit.NANOSECONDS);
                    }
                } catch (IOException | TesseractException e) {
                    results.add(new PageResult("", true, page, true, e.getMessage()));
                    if (Thread.currentThread().isInterrupted()) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                } catch (RuntimeException e) {
                    results.add(new PageResult("", true, page, true, e.getMessage()));
                    continue;
                }

                checkDeadline(deadlineNanos);
            } else {
                finalText = pageText;
            }

            results.add(new PageResult(finalText, useOcr, page));
        }

        checkDeadline(deadlineNanos);

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("OCR islemi kesintiye ugradi");
        }

        return results;
    }

    public static String extractText(PDDocument document)
            throws IOException, TesseractException, InterruptedException {
        return extractTextPerPage(document).stream()
                .map(PageResult::text)
                .collect(Collectors.joining("\n"));
    }

    public static boolean isTextUsable(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() < MIN_USABLE_TEXT_LENGTH) {
            return false;
        }
        long alphanumericCount = trimmed.codePoints()
                .filter(Character::isLetterOrDigit)
                .count();
        double ratio = (double) alphanumericCount / trimmed.length();
        return ratio >= MIN_ALPHANUMERIC_RATIO;
    }

    static boolean areImageDimensionsSafe(PDPage page, int dpi) {
        if (page == null) {
            return false;
        }

        PDRectangle box = page.getCropBox();
        if (box == null) {
            box = page.getMediaBox();
        }
        if (box == null) {
            return false;
        }

        double widthPt = box.getWidth();
        double heightPt = box.getHeight();

        if (Double.isNaN(widthPt) || Double.isInfinite(widthPt) || widthPt <= 0) {
            return false;
        }
        if (Double.isNaN(heightPt) || Double.isInfinite(heightPt) || heightPt <= 0) {
            return false;
        }

        int rotation = page.getRotation();
        if (rotation == 90 || rotation == 270) {
            double temp = widthPt;
            widthPt = heightPt;
            heightPt = temp;
        }

        double widthPx = widthPt * dpi / POINTS_PER_INCH;
        double heightPx = heightPt * dpi / POINTS_PER_INCH;

        if (Double.isNaN(widthPx) || Double.isInfinite(widthPx) || widthPx <= 0) {
            return false;
        }
        if (Double.isNaN(heightPx) || Double.isInfinite(heightPx) || heightPx <= 0) {
            return false;
        }

        if (widthPx > Double.MAX_VALUE / heightPx) {
            return false;
        }

        double totalPixels = widthPx * heightPx;
        if (Double.isNaN(totalPixels) || Double.isInfinite(totalPixels)) {
            return false;
        }

        return totalPixels <= MAX_IMAGE_PIXELS;
    }

    private static void checkDeadline(long deadlineNanos) throws IOException {
        if (System.nanoTime() >= deadlineNanos) {
            throw new IOException(
                    "Toplam islem butcesi (120 saniye) doldu. Analiz durduruldu."
            );
        }
    }

    public static String extractTextFromPage(PDDocument document, int pageIndex)
            throws IOException, TesseractException {
        return extractTextFromPage(document, pageIndex, null);
    }

    public static String extractTextFromPage(PDDocument document, int pageIndex, Function<BufferedImage, String> ocrFunction)
            throws IOException, TesseractException {
        int numPages = document.getNumberOfPages();
        if (pageIndex < 0 || pageIndex >= numPages) {
            throw new IndexOutOfBoundsException("pageIndex " + pageIndex + " out of bounds for " + numPages + " pages");
        }

        PDFRenderer renderer = new PDFRenderer(document);
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        String pageText = stripper.getText(document);

        boolean useOcr = !isTextUsable(pageText);
        String finalText;

        if (useOcr) {
            PDPage pdfPage = document.getPage(pageIndex);
            if (!areImageDimensionsSafe(pdfPage, RENDER_DPI)) {
                throw new IOException(
                        "OCR goruntusu piksel sinirini asti: en fazla " + MAX_IMAGE_PIXELS + " piksel izlenir."
                );
            }
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI);
            boolean windows = System.getProperty("os.name")
                    .toLowerCase(Locale.ROOT)
                    .contains("win");
            if (ocrFunction != null) {
                finalText = ocrFunction.apply(image);
            } else if (windows) {
                Tesseract tesseract = new Tesseract();
                tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
                tesseract.setLanguage(resolveOcrLanguage());
                tesseract.setPageSegMode(3);
                tesseract.setVariable("user_defined_dpi", "300");
                finalText = tesseract.doOCR(image);
            } else {
                finalText = runTesseract(image);
            }
        } else {
            finalText = pageText;
        }

        return finalText;
    }

    private static String runTesseract(BufferedImage image)
            throws IOException {
        return runTesseract(image, 60, TimeUnit.SECONDS,
                defaultTesseractCommand());
    }

    static String runTesseract(
            BufferedImage image, long timeout, TimeUnit unit)
            throws IOException {
        return runTesseract(image, timeout, unit,
                defaultTesseractCommand());
    }

    static String runTesseract(
            BufferedImage image, long timeout, TimeUnit unit,
            List<String> command)
            throws IOException {

        File imageFile = null;
        File outFile = null;
        File errFile = null;
        Process process = null;

        try {
            imageFile = File.createTempFile("cv-ocr-", ".png");
            outFile = File.createTempFile("cv-ocr-out-", ".txt");
            errFile = File.createTempFile("cv-ocr-err-", ".txt");

            if (!ImageIO.write(image, "png", imageFile)) {
                throw new IOException(
                        "OCR goruntusu PNG olarak kaydedilemedi."
                );
            }

            List<String> resolved = new ArrayList<>(command.size());
            for (String arg : command) {
                resolved.add("%IMAGE%".equals(arg)
                        ? imageFile.getAbsolutePath()
                        : arg);
            }

            ProcessBuilder builder = new ProcessBuilder(resolved);

            builder.redirectOutput(outFile);
            builder.redirectError(errFile);

            process = builder.start();

            try {
                if (!process.waitFor(timeout, unit)) {
                    boolean terminated =
                            destroyForciblyAndWait(
                                    process, 5, TimeUnit.SECONDS);
                    throw new IOException(
                            "OCR islemi zaman asimina ugradi."
                                    + (terminated
                                    ? ""
                                    : " Subprocess sonlandirilamadi.")
                    );
                }

                if (process.exitValue() != 0) {
                    throw new IOException(
                            "Tesseract OCR hatasi. Cikis kodu: "
                                    + process.exitValue()
                                    + " stderr: "
                                    + readStderr(errFile)
                    );
                }

                return Files.readString(
                        outFile.toPath(), StandardCharsets.UTF_8
                );

            } catch (InterruptedException e) {
                boolean terminated =
                        destroyForciblyAndWait(
                                process, 5, TimeUnit.SECONDS);
                Thread.currentThread().interrupt();
                throw new IOException(
                        "OCR islemi kesintiye ugradi."
                                + (terminated
                                ? ""
                                : " Subprocess sonlandirilamadi."),
                        e
                );
            } catch (IOException e) {
                // Subprocess baslatildiktan sonra beklenmeyen IOException:
                // process hala hayattasa zorla sonlandirilir.
                if (process.isAlive()) {
                    boolean terminated =
                            destroyForciblyAndWait(
                                    process, 5, TimeUnit.SECONDS);
                    if (!terminated) {
                        e.addSuppressed(new IOException(
                                "Subprocess sonlandirilamadi, hala aktif."));
                    }
                }
                throw e;
            }

        } finally {
            if (process == null) {
                // Subprocess baslatilmadi (start oncecesi hata verdi):
                // dosyalar hemen temizlenir.
                deleteQuietly(imageFile);
                deleteQuietly(outFile);
                deleteQuietly(errFile);
            } else if (!process.isAlive()) {
                // Subprocess zaten sonlandirildi: dosyalar hemen temizlenir.
                deleteQuietly(imageFile);
                deleteQuietly(outFile);
                deleteQuietly(errFile);
            } else {
                // Subprocess hala aktif. Dosyalari silmek yerine,
                // best-effort temizlik icin daemon bir thread baslatiriz.
                // Bu thread JVM kapanisini engellemez (daemon).
                scheduleCleanup(
                        new File[]{imageFile, outFile, errFile},
                        process
                );
            }
        }
    }

    /**
     * Subprocess hala calistirken temp dosyalarin temizligini
     * bounded bir bekleme sonrasi yapar. Daemon thread oldugu icin
     * JVM kapanisini engellemez; asil exception korunur.
     *
     * <p>Subprocess 10 saniye sonra hala aktifse, dosyalari silmez.
     * Dosyalar ancak subprocess'in gercekten sonlandirildigi
     * dogrulandiktan sonra silinir.</p>
     */
    private static void scheduleCleanup(File[] files, Process process) {
        Thread cleaner = new Thread(() -> {
            try {
                // Subprocess'in sonlanmasini bounded sureyle bekler.
                process.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // Thread kesintiye ugrarsa yine de temizligi yapmayi dener.
            }

            // Sadece subprocess gercekten sonlandirildiysa dosyalari sil.
            // Hala aktifse, dosyalari silmez (acik handle sorunu olabilir).
            if (!process.isAlive()) {
                for (File f : files) {
                    deleteQuietly(f);
                }
            }
        });
        cleaner.setDaemon(true);
        cleaner.start();
    }

    static void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {
            // Temizlik hatasi asil exception'i mask etmemeli.
        }
    }

    static boolean destroyForciblyAndWait(
            Process process, long timeout, TimeUnit unit) {
        process.destroyForcibly();
        try {
            return process.waitFor(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static String readStderr(File errFile) {
        byte[] buf = new byte[8192];
        int n;
        try (InputStream in = Files.newInputStream(errFile.toPath())) {
            n = in.readNBytes(buf, 0, buf.length);
        } catch (IOException e) {
            return "";
        }
        if (n <= 0) {
            return "";
        }
        return new String(buf, 0, n, StandardCharsets.UTF_8);
    }
}
