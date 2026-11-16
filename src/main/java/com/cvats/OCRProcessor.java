package com.cvats;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.apache.pdfbox.pdmodel.PDDocument;
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

    public record PageResult(String text, boolean ocrUsed, int pageIndex,
                             boolean ocrFailed, String errorMessage) {

        public PageResult(String text, boolean ocrUsed, int pageIndex) {
            this(text, ocrUsed, pageIndex, false, null);
        }
    }

    public static List<PageResult> extractTextPerPage(PDDocument document)
            throws IOException, TesseractException, InterruptedException {
        return extractTextPerPage(document, null);
    }

    public static List<PageResult> extractTextPerPage(PDDocument document, Function<BufferedImage, String> ocrFunction)
            throws IOException, TesseractException, InterruptedException {

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
            tesseract.setLanguage("eng");
            tesseract.setPageSegMode(3);
            tesseract.setVariable("user_defined_dpi", "300");
        }

        List<PageResult> results = new ArrayList<>();

        for (int page = 0; page < document.getNumberOfPages(); page++) {
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

            String finalText;
            if (useOcr) {
                BufferedImage image;
                try {
                    image = renderer.renderImageWithDPI(page, 300);
                } catch (IOException e) {
                    results.add(new PageResult("", true, page, true, e.getMessage()));
                    continue;
                }

                try {
                    if (ocrFunction != null) {
                        finalText = ocrFunction.apply(image);
                    } else if (windows) {
                        finalText = tesseract.doOCR(image);
                    } else {
                        finalText = runTesseract(image);
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
            } else {
                finalText = pageText;
            }

            results.add(new PageResult(finalText, useOcr, page));
        }

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
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300);
            boolean windows = System.getProperty("os.name")
                    .toLowerCase(Locale.ROOT)
                    .contains("win");
            if (ocrFunction != null) {
                finalText = ocrFunction.apply(image);
            } else if (windows) {
                Tesseract tesseract = new Tesseract();
                tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
                tesseract.setLanguage("eng");
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

    private static final List<String> DEFAULT_TESSERACT_COMMAND =
            List.of(
                    "tesseract",
                    "%IMAGE%",
                    "stdout",
                    "-l", "eng",
                    "--psm", "3",
                    "--dpi", "300"
            );

    private static String runTesseract(BufferedImage image)
            throws IOException {
        return runTesseract(image, 60, TimeUnit.SECONDS);
    }

    static String runTesseract(
            BufferedImage image, long timeout, TimeUnit unit)
            throws IOException {
        return runTesseract(image, timeout, unit,
                DEFAULT_TESSERACT_COMMAND);
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
