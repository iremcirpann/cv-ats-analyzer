
package com.cvats;

import java.util.Scanner;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String text = stripper.getText(document);
            boolean ocrUsed = false;


            if (text.isBlank()) {

                System.out.println("PDF metin icermiyor. OCR baslatiliyor...");

                ocrUsed = true;

                text = OCRProcessor.extractText(document);
            }


            System.out.println("\n=== CV ICERIGI ===");


            if (text.isBlank()) {

                System.out.println("Metin bulunamadi. OCR gerekebilir.");

            } else {

                System.out.println(text);

                ATSAnalyzer.analyze(text, ocrUsed);

            }



        } catch (IOException | TesseractException e) {

            System.out.println(
                    "PDF veya OCR hatasi: " + e.getMessage()
            );

        }

        scanner.close();
    }
}
