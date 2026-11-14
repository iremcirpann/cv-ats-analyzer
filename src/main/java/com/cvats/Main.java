package com.cvats;

import java.util.List;
import java.util.Scanner;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import net.sourceforge.tess4j.TesseractException;

public class Main {

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

        try (PDDocument document = Loader.loadPDF(pdfFile)) {

            List<OCRProcessor.PageResult> pageResults = OCRProcessor.extractTextPerPage(document);

            StringBuilder text = new StringBuilder();
            boolean ocrUsed = false;

            for (OCRProcessor.PageResult pr : pageResults) {
                text.append(pr.text());
                if (pr != pageResults.get(pageResults.size() - 1)) {
                    text.append("\n");
                }
                if (pr.ocrUsed()) {
                    ocrUsed = true;
                }
            }

            System.out.println("\n=== CV ICERIGI ===");

            if (text.isEmpty() || text.toString().isBlank()) {
                System.out.println("Metin bulunamadi. OCR gerekebilir.");
            } else {
                System.out.println(text);
                ATSAnalyzer.analyze(text.toString(), ocrUsed);
            }

        } catch (IOException | TesseractException e) {

            System.out.println(
                    "PDF veya OCR hatasi: " + e.getMessage()
            );

        }

        scanner.close();
    }
}