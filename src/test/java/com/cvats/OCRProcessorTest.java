package com.cvats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regresyon testleri: Linux subprocess (CR-01) icin
 * resource-yonetimi yardimci metodlari.
 *
 * <p>Gerçek Tesseract veya Docker gerektirmez;
 * sadece {@code deleteQuietly} ve {@code readStderr} metotlari test edilir.</p>
 */
public class OCRProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteQuietlyNullDusturmeli() {
        // null gonderildiginde hicbir istisna atilmamalidir.
        OCRProcessor.deleteQuietly(null);
    }

    @Test
    void deleteQuietlyVarDosyayiSilmeli() throws IOException {
        Path file = tempDir.resolve("var-olacak.txt");
        Files.writeString(file, "ornek icerik", StandardCharsets.UTF_8);
        assertTrue(Files.exists(file));

        OCRProcessor.deleteQuietly(file.toFile());

        assertFalse(Files.exists(file));
    }

    @Test
    void deleteQuietlyYokDosyayiSilmeli() {
        Path file = tempDir.resolve("yok-olsun.txt");
        assertFalse(Files.exists(file));

        // Dosya zaten yok; metot hata atmadan devam etmeli.
        OCRProcessor.deleteQuietly(file.toFile());
    }

    @Test
    void readStderrUTF8MetniDonmeli() throws IOException {
        Path file = tempDir.resolve("err.txt");
        String expected = "Tesseract hatasi: dil modeli bulunamadi";
        Files.writeString(file, expected, StandardCharsets.UTF_8);

        String actual = OCRProcessor.readStderr(file.toFile());

        assertEquals(expected, actual);
    }

    @Test
    void readStderrEnFazla8192ByteOkumali() throws IOException {
        Path file = tempDir.resolve("big-err.txt");
        // 8192 byte'dan uzun icerik olustur
        byte[] data = new byte[20000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ('A' + (i % 26));
        }
        Files.write(file, data);

        String actual = OCRProcessor.readStderr(file.toFile());

        assertEquals(8192, actual.getBytes(StandardCharsets.UTF_8).length,
                "readStderr en fazla 8192 byte dondurmeli.");
    }

    @Test
    void readStderrBosDosyaDonmeli() throws IOException {
        Path file = tempDir.resolve("empty-err.txt");
        Files.createFile(file);

        String actual = OCRProcessor.readStderr(file.toFile());

        assertEquals("", actual);
    }

    @Test
    void readStderrGecersizYolBosDonmeli() {
        Path file = tempDir.resolve("olmayan-err.txt");
        assertFalse(Files.exists(file));

        String actual = OCRProcessor.readStderr(file.toFile());

        assertEquals("", actual,
                "Dosya yoksa readStderr exception atmadan bos dondurmeli.");
    }

    // --- CR-01: Linux subprocess regresyon testleri (fake cmd.exe) ---

    @Test
    void runTesseractBasariliCikisDonmeli() throws IOException {
        BufferedImage image = new BufferedImage(10, 10,
                BufferedImage.TYPE_INT_RGB);
        List<String> cmd = List.of(
                "cmd.exe", "/c", "echo Basarili OCR sonucu"
        );

        String result = OCRProcessor.runTesseract(
                image, 60, TimeUnit.SECONDS, cmd);

        assertEquals("Basarili OCR sonucu", result.trim(),
                "Basarili cikisda OCR sonucu donmeli.");
    }

    @Test
    void runTesseractHataKoduAtar() throws IOException {
        BufferedImage image = new BufferedImage(10, 10,
                BufferedImage.TYPE_INT_RGB);
        List<String> cmd = List.of(
                "cmd.exe", "/c",
                "echo Hata mesaji 1>&2 & exit 1"
        );

        IOException ex = assertThrows(
                IOException.class,
                () -> OCRProcessor.runTesseract(
                        image, 60, TimeUnit.SECONDS, cmd));

        assertTrue(ex.getMessage().contains("Cikis kodu: 1"),
                "Cikis kodu 1 exception'ta yer almali.");
        assertTrue(ex.getMessage().contains("Hata mesaji"),
                "Stderr icerigi exception'ta yer almali.");
    }

    @Test
    void runTesseractZamanAsimiAtar() throws IOException {
        BufferedImage image = new BufferedImage(10, 10,
                BufferedImage.TYPE_INT_RGB);
        List<String> cmd = List.of(
                "cmd.exe", "/c",
                "for /L %i in (1,1,100000000) do rem"
        );

        IOException ex = assertThrows(
                IOException.class,
                () -> OCRProcessor.runTesseract(
                        image, 200, TimeUnit.MILLISECONDS, cmd));

        assertTrue(ex.getMessage().contains("zaman asimina"),
                "Zaman asimi exception'ta yer almali.");
    }
}