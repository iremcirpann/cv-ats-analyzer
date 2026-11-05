package com.cvats;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    private static String runTesseract(BufferedImage image)
            throws IOException {

        File imageFile = File.createTempFile("cv-ocr-", ".png");

        try {
            if (!ImageIO.write(image, "png", imageFile)) {
                throw new IOException(
                        "OCR goruntusu PNG olarak kaydedilemedi."
                );
            }

            ProcessBuilder builder = new ProcessBuilder(
                    "tesseract",
                    imageFile.getAbsolutePath(),
                    "stdout",
                    "-l", "eng",
                    "--psm", "3",
                    "--dpi", "300"
            );

            builder.redirectError(ProcessBuilder.Redirect.DISCARD);

            Process process = builder.start();

            byte[] output;
            try {
                output = process.getInputStream().readAllBytes();

                boolean finished =
                        process.waitFor(60, TimeUnit.SECONDS);

                if (!finished) {
                    process.destroyForcibly();
                    throw new IOException(
                            "OCR islemi zaman asimina ugradi."
                    );
                }

                if (process.exitValue() != 0) {
                    throw new IOException(
                            "Tesseract OCR hatasi. Cikis kodu: "
                                    + process.exitValue()
                    );
                }

            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException(
                        "OCR islemi kesintiye ugradi.", e
                );
            }

            return new String(output, StandardCharsets.UTF_8);

        } finally {
            Files.deleteIfExists(imageFile.toPath());
        }
    }
}
