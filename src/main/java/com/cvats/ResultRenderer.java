package com.cvats;

import java.util.Locale;

public class ResultRenderer {

    public static void render(AnalysisResult result) {
        System.out.println("\n=== ATS ANALIZ RAPORU ===");

        if (result.ocrUsed()) {
            for (String warning : result.ocrWarnings()) {
                System.out.println("[UYARI] " + warning);
            }
        }

        for (CriterionResult criterion : result.criteria()) {
            printResult(criterion.name(), criterion.found(), result.ocrUsed());
        }

        System.out.println("\n=== ANALIZ OZETI ===");

        System.out.println(
                "Tespit edilen kriter: " + result.foundCount() + "/" + result.totalCriteria()
        );

        System.out.printf(
                Locale.US,
                "Yapisal uygunluk orani: %%%.1f%n",
                result.structuralScore()
        );

        System.out.println(
                "Not: Bu oran yalnizca uygulamanin kontrol ettigi " +
                        "6 yapisal kritere dayanir. Gercek bir ATS puani degildir."
        );

        if (result.ocrUsed()) {
            System.out.println(
                    "Not: OCR hatalari nedeniyle bu sonuc " +
                            "kesin bir ATS uyumluluk puani degildir."
            );
        }

        System.out.println(
                "LinkedIn: " + (result.linkedInFound() ? "Bulundu" : "Bulunamadi")
        );

        System.out.println(
                "GitHub: " + (result.githubFound() ? "Bulundu" : "Bulunamadi")
        );

        System.out.println(
                "Olculebilir basari ifadesi: " +
                        (result.measurableResultsFound() ? "Bulundu" : "Bulunamadi")
        );

        System.out.println("\n=== IYILESTIRME ONERILERI ===");

        for (String recommendation : result.recommendations()) {
            System.out.println(recommendation);
        }
    }

    private static void printResult(String title, boolean found, boolean ocrUsed) {
        if (found) {
            System.out.println("[OK] " + title + " bulundu.");
        } else if (ocrUsed) {
            System.out.println(
                    "[UYARI] " + title +
                            " OCR sonucunda tespit edilemedi. " +
                            "Orijinal PDF'yi kontrol edin."
            );
        } else {
            System.out.println(
                    "[!!] " + title + " tespit edilemedi."
            );
        }
    }
}
