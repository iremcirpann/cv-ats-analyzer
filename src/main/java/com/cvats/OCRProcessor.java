package com.cvats;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

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

public class OCRProcessor {

    public static String extractText(PDDocument document)
            throws IOException, TesseractException {

        PDFRenderer renderer = new PDFRenderer(document);
        StringBuilder result = new StringBuilder();

        boolean windows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("win");

        Tesseract tesseract = null;

        if (windows) {
            tesseract = new Tesseract();
            tesseract.setDatapath(
                    "C:\\Program Files\\Tesseract-OCR\\tessdata"
            );
            tesseract.setLanguage("eng");
            tesseract.setPageSegMode(3);
            tesseract.setVariable("user_defined_dpi", "300");
        }

        for (int page = 0;
             page < document.getNumberOfPages();
             page++) {

            BufferedImage image =
                    renderer.renderImageWithDPI(page, 300);

            if (windows) {
                result.append(tesseract.doOCR(image));
            } else {
                result.append(runTesseract(image));
            }

            result.append("\n");
        }

        return result.toString();
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
