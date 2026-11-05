
package com.cvats;

import java.util.Locale;
import java.util.regex.Pattern;

public class ATSAnalyzer {

    public static void analyze(String text, boolean ocrUsed) {

        System.out.println("\n=== ATS ANALIZ RAPORU ===");

        if (ocrUsed) {
            System.out.println(
                    "[UYARI] Bu PDF OCR ile okunmustur. " +
                            "Bazi karakterler hatali taninmis olabilir."
            );
        }

        boolean email = Pattern.compile(
                "[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"
        ).matcher(text).find();

        boolean phone = Pattern.compile(
                "(?<!\\d)(?:\\+90\\s*|0)?5\\d{2}[\\s.-]*\\d{3}[\\s.-]*\\d{2}[\\s.-]*\\d{2}(?!\\d)"
        ).matcher(text).find();

        boolean education = hasSection(
                text, "eğitim", "education", "eğitim bilgileri"
        );

        boolean experience = hasSection(
                text, "deneyim", "experience",
                "iş deneyimi", "work experience"
        );

        boolean skills = hasSection(
                text, "yetkinlikler", "beceriler",
                "skills", "technical skills",
                "teknik beceriler"
        );

        boolean projects = hasSection(
                text, "projeler", "projects",
                "kişisel projeler", "akademik projeler"
        );

        printResult("E-posta", email, ocrUsed);
        printResult("Telefon", phone, ocrUsed);
        printResult("Egitim", education, ocrUsed);
        printResult("Deneyim", experience, ocrUsed);
        printResult("Yetkinlikler", skills, ocrUsed);
        printResult("Projeler", projects, ocrUsed);

        int foundCount = 0;

        if (email) foundCount++;
        if (phone) foundCount++;
        if (education) foundCount++;
        if (experience) foundCount++;
        if (skills) foundCount++;
        if (projects) foundCount++;
        double structuralScore = (foundCount / 6.0) * 100;
        String lowerText = text.toLowerCase(Locale.ROOT);

        boolean linkedin = lowerText.contains("linkedin.com");
        boolean github = lowerText.contains("github.com");

        boolean measurableResults = hasMeasurableResults(text);

        System.out.println("\n=== ANALIZ OZETI ===");

        System.out.println(
                "Tespit edilen kriter: " + foundCount + "/6"
        );

        System.out.printf(
                Locale.US,
                "Yapisal uygunluk orani: %%%.1f%n",
                structuralScore
        );

        System.out.println(
                "Not: Bu oran yalnizca uygulamanin kontrol ettigi " +
                        "6 yapisal kritere dayanir. Gercek bir ATS puani degildir."
        );

        if (ocrUsed) {
            System.out.println(
                    "Not: OCR hatalari nedeniyle bu sonuc " +
                            "kesin bir ATS uyumluluk puani degildir."
            );
        }

        System.out.println(
                "LinkedIn: " + (linkedin ? "Bulundu" : "Bulunamadi")
        );

        System.out.println(
                "GitHub: " + (github ? "Bulundu" : "Bulunamadi")
        );

        System.out.println(
                "Olculebilir basari ifadesi: " +
                        (measurableResults ? "Bulundu" : "Bulunamadi")
        );

        System.out.println("\n=== IYILESTIRME ONERILERI ===");

        if (!linkedin) {
            System.out.println(
                    "- LinkedIn profilinizi eklemeyi dusunebilirsiniz."
            );
        }

        if (!github) {
            System.out.println(
                    "- Teknik projeleriniz varsa GitHub baglantisi ekleyin."
            );
        }

        if (!measurableResults && experience) {
            System.out.println(
                    "- Deneyimlerinizi mumkunse olculebilir " +
                            "sonuclarla destekleyin."
            );
        }

        printRecommendation(
                email, ocrUsed,
                "E-posta adresinizi ekleyin.",
                "E-posta adresini orijinal PDF'den kontrol edin."
        );

        printRecommendation(
                phone, ocrUsed,
                "Telefon numaranizi ekleyin.",
                "Telefon numarasini orijinal PDF'den kontrol edin."
        );

        printRecommendation(
                education, ocrUsed,
                "Egitim bolumunu ekleyin.",
                "Egitim basligini orijinal PDF'den kontrol edin."
        );

        printRecommendation(
                experience, ocrUsed,
                "Deneyim veya stajlarinizi belirtin.",
                "Deneyim basligini orijinal PDF'den kontrol edin."
        );

        printRecommendation(
                skills, ocrUsed,
                "Teknik becerilerinizi listeleyin.",
                "Yetkinlikler basligini orijinal PDF'den kontrol edin."
        );

        printRecommendation(
                projects, ocrUsed,
                "Projelerinizi ayri bir bolumde sunun.",
                "Projeler basligini orijinal PDF'den kontrol edin."
        );
    }

    private static void printResult(
            String title, boolean found, boolean ocrUsed) {

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

    private static void printRecommendation(
            boolean found,
            boolean ocrUsed,
            String normalMessage,
            String ocrMessage) {

        if (!found) {
            System.out.println(
                    "- " + (ocrUsed ? ocrMessage : normalMessage)
            );
        }
    }

    private static boolean hasSection(
            String text, String... headings) {

        String[] lines = text.split("\\R");

        for (String line : lines) {
            String cleanedLine = normalizeText(line);

            for (String heading : headings) {
                if (cleanedLine.equals(normalizeText(heading))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasMeasurableResults(String text) {

        String[] lines = text.split("\\R");
        boolean relevantSection = false;

        Pattern headingPattern = Pattern.compile(
                "^(deneyim|is deneyimi|experience|work experience|" +
                        "projeler|projects|kisisel projeler|akademik projeler)$"
        );

        Pattern otherHeadingPattern = Pattern.compile(
                "^(egitim|education|yetkinlikler|beceriler|skills|" +
                        "technical skills|teknik beceriler|diller|languages|" +
                        "sertifikalar|certificates|certifications|hakkimda|" +
                        "about me|ozet|summary|iletisim|contact)$"
        );

        Pattern numberPattern = Pattern.compile(
                "(?<![\\p{L}\\d])(?:%\\s*)?\\d+(?:[.,]\\d+)?" +
                        "(?:\\s*%)?(?![\\p{L}\\d])"
        );

        Pattern actionPattern = Pattern.compile(
                "\\b(?:artirdim|arttirdim|artirildi|artti|" +
                        "azalttim|azaltildi|azaldi|iyilestirdim|" +
                        "iyilestirildi|tamamladim|tamamlandi|" +
                        "gelistirdim|gelistirildi|yonettim|" +
                        "yonetildi|ulastim|ulasildi|sagladim|" +
                        "saglandi|hizlandirdim|hizlandirildi|" +
                        "reduced|increased|improved|completed|" +
                        "developed|managed|delivered|achieved)\\b"
        );

        for (String line : lines) {

            String normalized = normalizeText(line);

            if (headingPattern.matcher(normalized).matches()) {
                relevantSection = true;
                continue;
            }

            if (otherHeadingPattern.matcher(normalized).matches()) {
                relevantSection = false;
                continue;
            }

            if (!relevantSection) {
                continue;
            }

            String[] sentences = normalized.split("[.!?;\\n]+");

            for (String sentence : sentences) {
                boolean hasNumber =
                        numberPattern.matcher(sentence).find();

                boolean hasAction =
                        actionPattern.matcher(sentence).find();

                if (hasNumber && hasAction) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String normalizeText(String text) {

        return text.toLowerCase(
                        Locale.forLanguageTag("tr-TR"))
                .replace("ı", "i")
                .replace("ğ", "g")
                .replace("ü", "u")
                .replace("ş", "s")
                .replace("ö", "o")
                .replace("ç", "c")
                .trim();
    }
}
