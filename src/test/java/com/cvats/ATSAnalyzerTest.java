package com.cvats;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ATSAnalyzerTest {

    @Test
    void tumKriterlerBulundugundaPuanYuzOlmali() {

        String cv = """
                E-posta: test@example.com
                Telefon: 0555 123 45 67

                Eğitim
                Bilgisayar Mühendisliği

                Deneyim
                Java Developer

                Yetkinlikler
                Java, SQL, Docker

                Projeler
                CV ATS Analyzer
                """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertEquals(6, result.foundCount(), "Altı kriterin tamamı bulunmalı.");
        assertEquals(6, result.totalCriteria());
        assertEquals(100.0, result.structuralScore(), "Yapısal uygunluk oranı %100 olmalı.");
        assertTrue(
                result.criteria().stream().allMatch(CriterionResult::found),
                "Her kriter found olmalı."
        );
    }

    @Test
    void bicimlendirilmisBasliklarTaninmali() {

        String cv = """
            Eğitim:
            Bilgisayar Mühendisliği

            • Deneyim
            Java Developer

            1. Projeler
            CV ATS Analyzer
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(criterionFound(result, "Egitim"));
        assertTrue(criterionFound(result, "Deneyim"));
        assertTrue(criterionFound(result, "Projeler"));
    }

    @Test
    void normalCumlelerBaslikSayilmamali() {

        String cv = """
            Deneyim kazanmak istiyorum.
            Eğitim hayatım boyunca Java öğrendim.
            Projeler üzerinde çalışmayı seviyorum.
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertFalse(criterionFound(result, "Egitim"));
        assertFalse(criterionFound(result, "Deneyim"));
        assertFalse(criterionFound(result, "Projeler"));
    }

    @Test
    void stajBasligiDeneyimOlarakTaninmali() {

        String cv = """
            Staj
            Yazılım Geliştirme Stajyeri
            Java ile uygulama geliştirdim.
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(criterionFound(result, "Deneyim"));
    }

    @Test
    void stajdakiOlculebilirBasariTaninmali() {

        String cv = """
            Staj
            Uygulama performansini %30 artirdim.
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(result.measurableResultsFound());
    }

    @Test
    void sifirKriterBulundugundaSkorSifir() {

        String cv = "Merhaba mundo.";

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertEquals(0, result.foundCount());
        assertEquals(6, result.totalCriteria());
        assertEquals(0.0, result.structuralScore());
    }

    @Test
    void yariKriterBulundugundaSkorYuzdeYez() {

        String cv = """
                E-posta: test@example.com
                Telefon: 0555 123 45 67

                Eğitim
                Bilgisayar Mühendisliği
                """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertEquals(3, result.foundCount());
        assertEquals(50.0, result.structuralScore());
    }

    @Test
    void linkedInVeGitHubTespiti() {

        String cv = "linkedin.com/in/test github.com/test";

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(result.linkedInFound());
        assertTrue(result.githubFound());
    }

    @Test
    void ocrKullanildiğindeOcrWarningsDolu() {

        AnalysisResult result = ATSAnalyzer.analyze("E-posta: test@example.com", true);

        assertTrue(result.ocrUsed());
        assertFalse(result.ocrWarnings().isEmpty());
    }

    @Test
    void eksikKriterIcinOneriUretilir() {

        AnalysisResult result = ATSAnalyzer.analyze("Merhaba", false);

        assertTrue(
                result.recommendations().stream()
                        .anyMatch(r -> r.contains("LinkedIn")),
                "Eksik LinkedIn için öneri üretilmeli."
        );
    }

    @Test
    void rendererCiktiUyumlulugu() {

        String cv = """
                E-posta: test@example.com
                Telefon: 0555 123 45 67

                Eğitim
                Bilgisayar Mühendisliği

                Deneyim
                Java Developer

                Yetkinlikler
                Java, SQL, Docker

                Projeler
                CV ATS Analyzer
                """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            ResultRenderer.render(result);
        } finally {
            System.setOut(originalOut);
        }

        String rendered = output.toString(StandardCharsets.UTF_8);

        assertTrue(rendered.contains("=== ATS ANALIZ RAPORU ==="));
        assertTrue(rendered.contains("Tespit edilen kriter: 6/6"));
        assertTrue(rendered.contains("Yapisal uygunluk orani: %100.0"));
        assertTrue(rendered.contains("=== ANALIZ OZETI ==="));
        assertTrue(rendered.contains("=== IYILESTIRME ONERILERI ==="));
    }

    private static boolean criterionFound(AnalysisResult result, String name) {
        return result.criteria().stream()
                .anyMatch(c -> c.name().equalsIgnoreCase(name) && c.found());
    }

    @Test
    void alternatifBasliklarTaninmali() {
        String cv = """
            Education Background
            Bilgisayar Mühendisliği

            Professional Experience
            Java Developer

            Work History
            5 yıl

            Competencies
            Leadership

            Skill Set
            Java, SQL

            Personal Projects
            Open Source Katkıları

            Portfolio
            CV ATS Analyzer
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(criterionFound(result, "Egitim"));
        assertTrue(criterionFound(result, "Deneyim"));
        assertTrue(criterionFound(result, "Yetkinlikler"));
        assertTrue(criterionFound(result, "Projeler"));
    }

    @Test
    void maddeIsiklariTemizlenmeli() {
        String cv = """
            ► Deneyim
            Java Developer

            ▸ Projeler
            CV ATS Analyzer

            ▪ Yetkinlikler
            Java, SQL
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(criterionFound(result, "Deneyim"));
        assertTrue(criterionFound(result, "Projeler"));
        assertTrue(criterionFound(result, "Yetkinlikler"));
    }

    @Test
    void numaralandirmaFormatlariTaninmali() {
        String cv = """
            1: Eğitim
            Bilgisayar Mühendisliği

            2- Deneyim
            Java Developer

            A. Projeler
            CV ATS Analyzer

            a) Skills
            Java, SQL
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(criterionFound(result, "Egitim"));
        assertTrue(criterionFound(result, "Deneyim"));
        assertTrue(criterionFound(result, "Projeler"));
        assertTrue(criterionFound(result, "Yetkinlikler"));
    }

    @Test
    void turkceAlternatifBasliklarTaninmali() {
        String cv = """
            Eğitim Bilgileri
            Ankara Üniversitesi

            Profesyonel Deneyim
            5 yıl

            Yetenekler
            Java, SQL
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(criterionFound(result, "Egitim"));
        assertTrue(criterionFound(result, "Deneyim"));
        assertTrue(criterionFound(result, "Yetkinlikler"));
    }

    @Test
    void normalCumlelerdeAnahtarKelimelerBaslikSayilmamali() {
        String cv = """
            Eğitim-Life boyunca Java öğrendim.
            Deneyim kazanmak istiyorum.
            Staj yapmak istiyorum.
            Projeler üzerinde çalışıyorum.
            Skillslearned: Java, Python.
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertFalse(criterionFound(result, "Egitim"));
        assertFalse(criterionFound(result, "Deneyim"));
        assertFalse(criterionFound(result, "Projeler"));
        assertFalse(criterionFound(result, "Yetkinlikler"));
    }

    @Test
    void genelKelimelerBolumBasligiDegildir() {
        String cv = """
            Üniversite: Ankara Üniversitesi
            Mezun: 2024
            Lisans: Bilgisayar Mühendisliği
            Diller: Java, Python, İngilizce
            Teknik: Java, SQL, Docker
            Work: 5 yıl deneyim
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertFalse(criterionFound(result, "Egitim"));
        assertFalse(criterionFound(result, "Deneyim"));
        assertFalse(criterionFound(result, "Yetkinlikler"));
        assertFalse(criterionFound(result, "Projeler"));
    }

    @Test
    void basliklaBaslayanAciklamaCumleleriYanlisPozitifUretmez() {
        String cv = """
            Eğitim: 2020-2024 yılı arasında öğrenim aldım.
            Deneyim: 3 yıl Java developer olarak çalıştım.
            Staj: Yazılım geliştirme stajı_done.
            Projeler: CV ATS Analyzer projesi geliştirdim.
            Skills: Java, SQL, Docker becerilerim var.
            """;

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertFalse(criterionFound(result, "Egitim"));
        assertFalse(criterionFound(result, "Deneyim"));
        assertFalse(criterionFound(result, "Projeler"));
        assertFalse(criterionFound(result, "Yetkinlikler"));
    }

    @Test
    void parantezliTelefonNumarasiTaninmali() {
        String cv = "Telefon: (0555) 123 45 67";

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(criterionFound(result, "Telefon"));
    }

    @Test
    void uluslararasiFormatliTelefonTaninmali() {
        String cv = "Telefon: +90 (555) 123 45 67";

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(criterionFound(result, "Telefon"));
    }

    @Test
    void sadeceTelefonEtiketiNumaraSayilmamali() {
        String cv = "Telefon: gizli";

        AnalysisResult result = ATSAnalyzer.analyze(cv, true);

        assertFalse(criterionFound(result, "Telefon"));
    }

    @Test
    void farkliSatirlardakiRakamlarTelefonSayilmamali() {
        String cv = "500\n123\n45\n67";
        AnalysisResult result = ATSAnalyzer.analyze(cv, false);
        assertFalse(criterionFound(result, "Telefon"));
    }

    @Test
    void yabanciUlkeKoduTurkTelefonuSayilmamali() {
        String cv = "+1 (555) 123-4567";
        AnalysisResult result = ATSAnalyzer.analyze(cv, false);
        assertFalse(criterionFound(result, "Telefon"));
    }

    @Test
    void ciftSifirliUluslararasiFormatReddedilmeli() {
        String cv = "+90 0555 123 45 67";
        AnalysisResult result = ATSAnalyzer.analyze(cv, false);
        assertFalse(criterionFound(result, "Telefon"));
    }

    @Test
    void siparisNumarasiTelefonSayilmamali() {
        String cv = "Sipariş No: 5123456789";
        AnalysisResult result = ATSAnalyzer.analyze(cv, false);
        assertFalse(criterionFound(result, "Telefon"));
    }

    @Test
    void emailIleAyniSatirdakiTelefonTaninmali() {
        String cv = "E-posta: user+1@example.com Telefon: 0555 123 45 67";
        AnalysisResult result = ATSAnalyzer.analyze(cv, false);
        assertTrue(criterionFound(result, "Telefon"));
    }

    @Test
    void referansIleAyniSatirdakiTelefonTaninmali() {
        String cv = "Referans: Ahmet, Telefon: 0555 123 45 67";
        AnalysisResult result = ATSAnalyzer.analyze(cv, false);
        assertTrue(criterionFound(result, "Telefon"));
    }

    @Test
    void fazlaRakamliNumaraReddedilmeli() {
        String cv = "Telefon: 0555 123 45 67 89";
        AnalysisResult result = ATSAnalyzer.analyze(cv, false);
        assertFalse(criterionFound(result, "Telefon"));
    }

    @Test
    void harflereBitisikNumaraReddedilmeli() {
        String cv = "Kod: ABC5551234567";
        AnalysisResult result = ATSAnalyzer.analyze(cv, false);
        assertFalse(criterionFound(result, "Telefon"));
    }

    @Test
    void unicodeBoslukluTelefonTaninmali() {
        String cv = "Telefon: 0555\u00A0123\u00A045\u00A067";

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertTrue(criterionFound(result, "Telefon"));
    }

    @Test
    void yabanciUlkeKodu0044TelefonSayilmamali() {
        String cv = "Telefon: 0044 555 123 45 67";

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertFalse(criterionFound(result, "Telefon"));
    }

    @Test
    void egikCizgiSonrasiFazlaRakamReddedilmeli() {
        String cv = "Telefon: 0555 123 45 67/89";

        AnalysisResult result = ATSAnalyzer.analyze(cv, false);

        assertFalse(criterionFound(result, "Telefon"));
    }

}
