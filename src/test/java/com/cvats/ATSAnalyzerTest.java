package com.cvats;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

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

        PrintStream originalOut = System.out;
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(
                    output, true, StandardCharsets.UTF_8
            ));

            ATSAnalyzer.analyze(cv, false);

        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(
                result.contains("Tespit edilen kriter: 6/6"),
                "Altı kriterin tamamı bulunmalı."
        );

        assertTrue(
                result.contains("Yapisal uygunluk orani: %100.0"),
                "Yapısal uygunluk oranı %100 olmalı."
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

        PrintStream originalOut = System.out;
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(
                    output, true, StandardCharsets.UTF_8
            ));

            ATSAnalyzer.analyze(cv, false);

        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("[OK] Egitim bulundu."));
        assertTrue(result.contains("[OK] Deneyim bulundu."));
        assertTrue(result.contains("[OK] Projeler bulundu."));
    }

    @Test
    void normalCumlelerBaslikSayilmamali() {

        String cv = """
            Deneyim kazanmak istiyorum.
            Eğitim hayatım boyunca Java öğrendim.
            Projeler üzerinde çalışmayı seviyorum.
            """;

        PrintStream originalOut = System.out;
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(
                    output, true, StandardCharsets.UTF_8
            ));

            ATSAnalyzer.analyze(cv, false);

        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("[!!] Egitim tespit edilemedi."));
        assertTrue(result.contains("[!!] Deneyim tespit edilemedi."));
        assertTrue(result.contains("[!!] Projeler tespit edilemedi."));
    }

    @Test
    void stajBasligiDeneyimOlarakTaninmali() {

        String cv = """
            Staj
            Yazılım Geliştirme Stajyeri
            Java ile uygulama geliştirdim.
            """;

        PrintStream originalOut = System.out;
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(
                    output, true, StandardCharsets.UTF_8
            ));

            ATSAnalyzer.analyze(cv, false);

        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains("[OK] Deneyim bulundu."));
    }

    @Test
    void stajdakiOlculebilirBasariTaninmali() {

        String cv = """
            Staj
            Uygulama performansini %30 artirdim.
            """;

        PrintStream originalOut = System.out;
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(
                    output, true, StandardCharsets.UTF_8
            ));

            ATSAnalyzer.analyze(cv, false);

        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);

        assertTrue(result.contains(
                "Olculebilir basari ifadesi: Bulundu"
        ));
    }

}
