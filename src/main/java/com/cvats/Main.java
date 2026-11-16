package com.cvats;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.PDDocument;
import net.sourceforge.tess4j.TesseractException;

public class Main {

    static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;
    static final int MAX_TOTAL_SECONDS = 120;
    private static final int MAX_PAGE_COUNT = 50;

    static boolean dosyaBoyutuGecerli(long dosyaBoyutu) {
        return dosyaBoyutu <= MAX_FILE_SIZE_BYTES;
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        String pdfPath;
        Scanner scanner = null;

        try {
            if (args.length > 0) {
                if (args[0] == null || args[0].isBlank()) {
                    System.err.println("HATA: Gecersiz dosya yolu.");
                    return 2;
                }
                pdfPath = args[0].trim();
            } else {
                scanner = new Scanner(System.in);
                System.out.println("=== CV ATS ANALYZER ===");
                System.out.print("CV dosyasinin yolunu giriniz: ");

                if (!scanner.hasNextLine()) {
                    System.err.println("HATA: Giris okunamadi.");
                    return 2;
                }

                pdfPath = scanner.nextLine().trim();

                if (pdfPath.isEmpty()) {
                    System.err.println("HATA: Bos giris.");
                    return 2;
                }
            }

            File pdfFile = new File(pdfPath);

            if (!pdfFile.isFile()) {
                System.err.println("HATA: PDF dosyasi bulunamadi!");
                return 2;
            }

            if (!pdfPath.toLowerCase().endsWith(".pdf")) {
                System.err.println("HATA: Lutfen PDF dosyasi seciniz!");
                return 2;
            }

            if (!dosyaBoyutuGecerli(pdfFile.length())) {
                System.err.println(
                        "HATA: PDF dosyasi 25 MiB boyut sinirini asti. Lutfen daha kucuk bir dosya seciniz."
                );
                return 2;
            }

            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(MAX_TOTAL_SECONDS);

            try (PDDocument document = Loader.loadPDF(pdfFile)) {

                if (document.getNumberOfPages() > MAX_PAGE_COUNT) {
                    System.err.println(
                            "HATA: PDF dosyasi 50 sayfa sinirini asti. Lutfen daha kisa bir belge seciniz."
                    );
                    return 2;
                }

                List<OCRProcessor.PageResult> pageResults = OCRProcessor.extractTextPerPage(document, null, deadlineNanos);

                StringBuilder text = new StringBuilder();
                boolean ocrUsed = false;
                boolean anyFailure = false;
                int successfulPages = 0;
                boolean first = true;

                for (int i = 0; i < pageResults.size(); i++) {
                    OCRProcessor.PageResult pr = pageResults.get(i);

                    if (pr.ocrFailed()) {
                        anyFailure = true;
                        System.out.println("UYARI: Sayfa " + (pr.pageIndex() + 1)
                                + " OCR islemi basarisiz oldu: " + pr.errorMessage());
                        continue;
                    }

                    if (!first) {
                        text.append("\n");
                    }
                    first = false;
                    text.append(pr.text());
                    if (pr.ocrUsed()) {
                        ocrUsed = true;
                    }
                    successfulPages++;
                }

                System.out.println("\n=== CV ICERIGI ===");

                if (successfulPages == 0 || text.isEmpty() || text.toString().isBlank()) {
                    System.out.println("Hiçbir sayfadan kullanılabilir metin alınamadı. Analiz yapılamadı.");
                    return 1;
                } else {
                    System.out.println(text);
                    if (anyFailure) {
                        System.out.println("UYARI: Bazı sayfalar OCR hatası nedeniyle atlandı; analiz eksik sayfalar nedeniyle tamamlanamadı.");
                    }
                    ATSAnalyzer.analyze(text.toString(), ocrUsed);
                }

                return 0;

            } catch (InvalidPasswordException e) {
                System.err.println("HATA: PDF dosyasi sifreli. Sifre gereklidir.");
                return 1;
            } catch (IOException e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("Toplam islem butcesi")) {
                    System.err.println("HATA: " + msg);
                    return 1;
                } else {
                    System.err.println("PDF veya OCR hatasi: " + msg);
                    return 1;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("PDF veya OCR hatasi: " + e.getMessage());
                return 1;
            } catch (TesseractException e) {
                System.err.println("PDF veya OCR hatasi: " + e.getMessage());
                return 1;
            }

        } catch (NoSuchElementException e) {
            System.err.println("HATA: Giris okunamadi.");
            return 2;
        } finally {
            // System'inin kapanmasini onlemek icin Scanner(System.in) kapatilmaz.
        }
    }
}
