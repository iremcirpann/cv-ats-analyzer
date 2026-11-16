package com.cvats;

import java.util.List;
import java.util.Scanner;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.Loader;
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

        Scanner scanner = new Scanner(System.in);

        System.out.println("=== CV ATS ANALYZER ===");
        System.out.print("CV dosyasinin yolunu giriniz: ");

        String pdfPath = scanner.nextLine().trim();

        File pdfFile = new File(pdfPath);

        if (!pdfFile.isFile()) {
            System.out.println("HATA: PDF dosyasi bulunamadi!");
            scanner.close();
            return;
        }

        if (!pdfPath.toLowerCase().endsWith(".pdf")) {
            System.out.println("HATA: Lutfen PDF dosyasi seciniz!");
            scanner.close();
            return;
        }

        if (!dosyaBoyutuGecerli(pdfFile.length())) {
            System.out.println(
                    "HATA: PDF dosyasi 25 MiB boyut sinirini asti. Lutfen daha kucuk bir dosya seciniz."
            );
            scanner.close();
            return;
        }

        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(MAX_TOTAL_SECONDS);

        try (PDDocument document = Loader.loadPDF(pdfFile)) {

            if (document.getNumberOfPages() > MAX_PAGE_COUNT) {
                System.out.println(
                        "HATA: PDF dosyasi 50 sayfa sinirini asti. Lutfen daha kisa bir belge seciniz."
                );
                scanner.close();
                return;
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
            } else {
                System.out.println(text);
                if (anyFailure) {
                    System.out.println("UYARI: Bazı sayfalar OCR hatası nedeniyle atlandı; analiz eksik sayfalar nedeniyle tamamlanamadı.");
                }
                ATSAnalyzer.analyze(text.toString(), ocrUsed);
            }

        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("Toplam islem butcesi")) {
                System.out.println("HATA: " + msg);
            } else {
                System.out.println("PDF veya OCR hatasi: " + msg);
            }
        } catch (TesseractException | InterruptedException e) {
            System.out.println(
                    "PDF veya OCR hatasi: " + e.getMessage()
            );
        }

        scanner.close();
    }
}