import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.RenderingHints;

public class OcrBenchmark {

    private static final String EXPECTED = "05321234567";

    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+90[\\s().-]*|0)?5\\d{2}" +
                    "[\\s().-]*\\d{3}[\\s().-]*\\d{2}" +
                    "[\\s().-]*\\d{2}(?!\\d)"
    );

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                    "Kullanim: java OcrBenchmark <sayfa.png> <telefon.png>"
            );
            return;
        }

        Path page = Path.of(args[0]);
        Path phone = Path.of(args[1]);

        for (Path file : List.of(page, phone)) {
            if (!Files.isRegularFile(file) ||
                    !file.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".png")) {
                System.err.println("PNG bulunamadi: " + file);
                return;
            }
        }

        BufferedImage original = ImageIO.read(phone.toFile());

        if (original == null) {
            System.err.println("Telefon goruntusu okunamadi.");
            return;
        }

        System.out.println("=== OCR KARSILASTIRMA ===");
        System.out.println("Beklenen: " + EXPECTED);

        run("Tam sayfa / PSM 3", page, 3, false);
        run("Tam sayfa / PSM 6", page, 6, false);
        findPhoneLabels(page);

        Path grayFile = Files.createTempFile("ocr-gray-", ".png");
        Path bwFile = Files.createTempFile("ocr-bw-", ".png");

        try {
            BufferedImage gray = grayscale(original);
            BufferedImage bw = threshold(gray, 128);

            for (int factor : new int[]{2, 3, 4}) {

                Path enlargedFile = Files.createTempFile(
                        "ocr-upscale-" + factor + "x-", ".png"
                );

                try {
                    BufferedImage enlarged = upscale(original, factor);

                    ImageIO.write(
                            enlarged, "png", enlargedFile.toFile()
                    );

                    for (int psm : new int[]{6, 7}) {
                        run(
                                "Buyutulmus " + factor + "x / PSM " + psm,
                                enlargedFile,
                                psm,
                                true
                        );
                    }

                } finally {
                    Files.deleteIfExists(enlargedFile);
                }
            }

            ImageIO.write(gray, "png", grayFile.toFile());
            ImageIO.write(bw, "png", bwFile.toFile());

            for (int psm : new int[]{6, 7}) {
                run("Orijinal / PSM " + psm,
                        phone, psm, true);

                run("Gri tonlama / PSM " + psm,
                        grayFile, psm, true);

                run("Siyah-beyaz / PSM " + psm,
                        bwFile, psm, true);
            }

        } finally {
            Files.deleteIfExists(grayFile);
            Files.deleteIfExists(bwFile);
        }
    }

    private static BufferedImage upscale(
            BufferedImage source, int factor) {

        BufferedImage result = new BufferedImage(
                source.getWidth() * factor,
                source.getHeight() * factor,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = result.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );

            graphics.drawImage(
                    source,
                    0,
                    0,
                    result.getWidth(),
                    result.getHeight(),
                    null
            );
        } finally {
            graphics.dispose();
        }

        return result;
    }

    private static BufferedImage grayscale(
            BufferedImage source) {

        BufferedImage gray = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics2D graphics = gray.createGraphics();

        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        return gray;
    }

    private static BufferedImage threshold(
            BufferedImage gray, int limit) {

        BufferedImage result = new BufferedImage(
                gray.getWidth(),
                gray.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        for (int y = 0; y < gray.getHeight(); y++) {
            for (int x = 0; x < gray.getWidth(); x++) {

                int value = gray.getRaster()
                        .getSample(x, y, 0);

                int newValue = value >= limit ? 255 : 0;

                result.getRaster()
                        .setSample(x, y, 0, newValue);
            }
        }

        return result;
    }

    private static void run(
            String title,
            Path image,
            int psm,
            boolean phoneOnly) throws Exception {

        System.out.println("\n=== " + title + " ===");

        List<String> command = new ArrayList<>(List.of(
                "tesseract",
                image.toAbsolutePath().toString(),
                "stdout",
                "-l", "tur+eng",
                "--psm", Integer.toString(psm),
                "--dpi", "300"
        ));

        if (phoneOnly) {
            command.add("-c");
            command.add("tessedit_char_whitelist=0123456789");
        }

        Path stdout = Files.createTempFile("ocr-out-", ".txt");
        Path stderr = Files.createTempFile("ocr-err-", ".txt");

        Process process = null;
        long start = System.nanoTime();

        try {
            ProcessBuilder builder = new ProcessBuilder(command);

            builder.redirectOutput(stdout.toFile());
            builder.redirectError(stderr.toFile());

            process = builder.start();

            boolean finished = process.waitFor(
                    60, TimeUnit.SECONDS
            );

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                System.out.println("HATA: OCR zaman asimi.");
                return;
            }

            long elapsed = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - start
            );

            String output = Files.readString(
                    stdout, StandardCharsets.UTF_8
            );

            String errors = Files.readString(
                    stderr, StandardCharsets.UTF_8
            );

            System.out.println("Sure: " + elapsed + " ms");
            System.out.println("Ham OCR:\n" + output);

            if (process.exitValue() != 0) {
                System.out.println(
                        "HATA: Tesseract cikis kodu " +
                                process.exitValue()
                );
                System.out.println(errors);
                return;
            }

            Matcher matcher = PHONE.matcher(output);

            String detected = matcher.find()
                    ? matcher.group().replaceAll("[\\s().-]", "")
                    : "";

            if (phoneOnly && detected.isEmpty()) {
                detected = output.trim()
                        .replaceAll("[\\s().-]", "");
            }

            System.out.println("Tespit edilen: " + detected);
            System.out.println(
                    "Tam eslesme: " + EXPECTED.equals(detected)
            );

            if (!errors.isBlank()) {
                System.out.println(
                        "Tesseract uyarilari:\n" + errors
                );
            }

        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }

            Files.deleteIfExists(stdout);
            Files.deleteIfExists(stderr);
        }
    }

    private static void findPhoneLabels(Path image) throws Exception {

        System.out.println("\n=== TELEFON KONUM TESPITI ===");

        Path output = Files.createTempFile("ocr-tsv-", ".tsv");
        Path errors = Files.createTempFile("ocr-tsv-err-", ".txt");

        Process process = null;

        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "tesseract",
                    image.toAbsolutePath().toString(),
                    "stdout",
                    "-l", "tur+eng",
                    "--psm", "6",
                    "--dpi", "300",
                    "tsv"
            );

            builder.redirectOutput(output.toFile());
            builder.redirectError(errors.toFile());

            process = builder.start();

            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalStateException("TSV OCR zaman asimi.");
            }

            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "TSV OCR hatasi: " +
                                Files.readString(errors, StandardCharsets.UTF_8)
                );
            }

            List<String> lines = Files.readAllLines(
                    output, StandardCharsets.UTF_8
            );

            for (int i = 1; i < lines.size(); i++) {
                String[] fields = lines.get(i).split("\t", 12);

                if (fields.length < 12) {
                    continue;
                }
                if ("5".equals(fields[0]) && !fields[11].isBlank()) {
                    System.out.printf(
                            "TSV kelime: [%s] X=%s Y=%s W=%s H=%s%n",
                            fields[11], fields[6], fields[7],
                            fields[8], fields[9]
                    );
                }
                String word = fields[11]
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{L}]", "");

                if (List.of(
                        "telefon", "tel", "cep", "gsm",
                        "phone", "mobile", "cell"
                ).contains(word)) {

                    System.out.println("Bulunan kelime: " + fields[11]);
                    System.out.println("X: " + fields[6]);
                    System.out.println("Y: " + fields[7]);
                    System.out.println("Genislik: " + fields[8]);
                    System.out.println("Yukseklik: " + fields[9]);

                    int labelX = Integer.parseInt(fields[6]);
                    int labelY = Integer.parseInt(fields[7]);
                    int labelWidth = Integer.parseInt(fields[8]);
                    int labelHeight = Integer.parseInt(fields[9]);

                    BufferedImage fullPage = ImageIO.read(image.toFile());

// Telefon etiketinin sagindaki alani sec.
// Etiketten sonraki bolgeden basla, ancak
// ilk rakami kesmemek icin biraz sola kay.
                    int cropX = 790;

// Telefon satirinin saginda kalan tum alani al.
                    int cropWidth = 1120;
                    int cropY = Math.max(0, labelY - 10);

                    int cropHeight = Math.min(
                            labelHeight * 2,
                            fullPage.getHeight() - cropY
                    );

                    if (cropWidth <= 0 || cropHeight <= 0) {
                        System.out.println("Telefon alani kirpilamadi.");
                        continue;
                    }

                    BufferedImage cropped = fullPage.getSubimage(
                            cropX, cropY, cropWidth, cropHeight
                    );

                    BufferedImage enlarged = upscale(cropped, 3);

                    Path croppedFile = Files.createTempFile(
                            "ocr-auto-phone-", ".png"
                    );

                    try {
                        ImageIO.write(enlarged, "png", croppedFile.toFile());
                        Path debugFile = Path.of(
                                System.getProperty("user.home"),
                                "Desktop",
                                "otomatik-telefon-kirpma.png"
                        );

                        ImageIO.write(enlarged, "png", debugFile.toFile());

                        System.out.println(
                                "Otomatik kirpma goruntusu: " + debugFile
                        );
                        run("Otomatik telefon / 3x / PSM 6",
                                croppedFile, 6, true);

                        run("Otomatik telefon / 3x / PSM 7",
                                croppedFile, 7, true);

                    } finally {
                        Files.deleteIfExists(croppedFile);
                    }

                }
            }

        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }

            Files.deleteIfExists(output);
            Files.deleteIfExists(errors);
        }
    }

}
