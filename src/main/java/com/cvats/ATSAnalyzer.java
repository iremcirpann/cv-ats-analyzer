package com.cvats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class ATSAnalyzer {

    public static AnalysisResult analyze(String text, boolean ocrUsed) {

        boolean email = Pattern.compile(
                "[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}"
        ).matcher(text).find();

        boolean phoneNumberDetected = Pattern.compile(
                "(?<!\\d)(?:\\+90\\s*|0)?5\\d{2}[\\s.-]*\\d{3}[\\s.-]*\\d{2}[\\s.-]*\\d{2}(?!\\d)"
        ).matcher(text).find();

        boolean phoneLabelDetected = Pattern.compile(
                "(?im)^\\s*(?:telefon|phone|mobile|gsm|cep)\\s*:"
        ).matcher(text).find();

        boolean phone = phoneNumberDetected || (ocrUsed && phoneLabelDetected);

        boolean education = hasSection(
                text, "eğitim", "education", "eğitim bilgileri",
                "education background"
        );

        boolean experience = hasSection(
                text,
                "deneyim",
                "experience",
                "iş deneyimi",
                "work experience",
                "professional experience",
                "work history",
                "employment history",
                "profesyonel deneyim",
                "staj",
                "staj deneyimi",
                "internship",
                "internships"
        );

        boolean skills = hasSection(
                text, "yetkinlikler", "yetenekler", "beceriler",
                "skills", "technical skills",
                "teknik beceriler",
                "competencies", "skill set"
        );

        boolean projects = hasSection(
                text, "projeler", "projects",
                "kişisel projeler", "akademik projeler",
                "personal projects", "portfolio"
        );

        String lowerText = text.toLowerCase(Locale.ROOT);

        boolean linkedin = lowerText.contains("linkedin.com");
        boolean github = lowerText.contains("github.com");

        boolean measurableResults = hasMeasurableResults(text);

        List<String> ocrWarnings = new ArrayList<>();

        if (ocrUsed) {
            ocrWarnings.add(
                    "Bu PDF OCR ile okunmustur. " +
                            "Bazi karakterler hatali taninmis olabilir."
            );

            if (phone) {
                ocrWarnings.add(
                        "Telefon numarasi OCR ile tespit edildi. " +
                                "Numaranin rakamlari hatali okunmus olabilir. " +
                                "Orijinal PDF ile karsilastirin."
                );
            }
        }
        List<String> recommendations = new ArrayList<>();

        if (!linkedin) {
            recommendations.add(
                    "- LinkedIn profilinizi eklemeyi dusunebilirsiniz."
            );
        }

        if (!github) {
            recommendations.add(
                    "- Teknik projeleriniz varsa GitHub baglantisi ekleyin."
            );
        }

        if (!measurableResults && experience) {
            recommendations.add(
                    "- Deneyimlerinizi mumkunse olculebilir " +
                            "sonuclarla destekleyin."
            );
        }

        List<CriterionResult> criteria = new ArrayList<>();
        criteria.add(criterion("E-posta", email,
                "E-posta adresinizi ekleyin.",
                "E-posta adresini orijinal PDF'den kontrol edin."));
        criteria.add(criterion("Telefon", phone,
                "Telefon numaranizi ekleyin.",
                "Telefon numarasini orijinal PDF'den kontrol edin."));
        criteria.add(criterion("Egitim", education,
                "Egitim bolumunu ekleyin.",
                "Egitim basligini orijinal PDF'den kontrol edin."));
        criteria.add(criterion("Deneyim", experience,
                "Deneyim veya stajlarinizi belirtin.",
                "Deneyim basligini orijinal PDF'den kontrol edin."));
        criteria.add(criterion("Yetkinlikler", skills,
                "Teknik becerilerinizi listeleyin.",
                "Yetkinlikler basligini orijinal PDF'den kontrol edin."));
        criteria.add(criterion("Projeler", projects,
                "Projelerinizi ayri bir bolumde sunun.",
                "Projeler basligini orijinal PDF'den kontrol edin."));

        for (CriterionResult criterion : criteria) {
            if (!criterion.found()) {
                recommendations.add(
                        "- " + (ocrUsed
                                ? criterion.ocrRecommendation()
                                : criterion.recommendation())
                );
            }
        }

        return new AnalysisResult(
                List.copyOf(criteria),
                linkedin,
                github,
                measurableResults,
                ocrUsed,
                List.copyOf(ocrWarnings),
                List.copyOf(recommendations)
        );
    }

    private static CriterionResult criterion(
            String name,
            boolean found,
            String normalRecommendation,
            String ocrRecommendation
    ) {
        return new CriterionResult(
                name,
                found,
                found ? name : "",
                normalRecommendation,
                ocrRecommendation
        );
    }

    private static boolean hasSection(
            String text, String... headings
    ) {

        String[] lines = text.split("\\R");

        for (String line : lines) {

            String cleanedLine = normalizeText(line)
                    .replaceFirst("^[\\s\\p{Punct}•►▸▶▪▫·]+", "")
                    .replaceFirst("^(?:\\d+[:\\-.)]|[A-Za-z][.)])\\s*", "")
                    .replaceFirst("^[\\s\\p{Punct}•►▸▶▪▫·]+", "")
                    .replaceFirst("[\\s\\p{Punct}•►▸▶▪▫·]+$", "")
                    .trim();

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
                        "professional experience|work history|employment history|" +
                        "profesyonel deneyim|" +
                        "staj|staj deneyimi|internship|internships|" +
                        "projeler|projects|kisisel projeler|akademik projeler|" +
                        "personal projects|portfolio)$"
        );

        Pattern otherHeadingPattern = Pattern.compile(
                "^(egitim|education|egitim bilgileri|education background|" +
                        "yetkinlikler|yetenekler|beceriler|skills|" +
                        "technical skills|teknik beceriler|" +
                        "competencies|skill set|" +
                        "diller|languages|" +
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
