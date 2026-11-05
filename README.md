# CV ATS Analyzer — CV Analiz Uygulaması

## 1. Proje Hakkında

CV ATS Analyzer, Java programlama diliyle geliştirilmiş, PDF formatındaki özgeçmişleri analiz eden komut satırı uygulamasıdır.

Uygulama, PDF dosyalarından metin çıkararak özgeçmişin belirlenen yapısal kriterlere uygunluğunu değerlendirir ve kullanıcıya iyileştirme önerileri sunar.

Metin içermeyen, taranmış PDF dosyalarını okumak için OCR (Optik Karakter Tanıma) teknolojisi kullanılır.

Uygulama terminal üzerinden çalışır ve web arayüzü gerektirmez.

## 2. Kullanılan Teknolojiler

| Teknoloji | Kullanım amacı |
|---|---|
| Java 21 | Uygulamanın geliştirilmesi |
| Apache Maven | Bağımlılık ve proje yönetimi |
| Apache PDFBox 3.0.5 | PDF dosyalarından metin çıkarma |
| Tess4J 5.16.0 | Java ile Tesseract OCR entegrasyonu |
| Tesseract OCR | Taranmış PDF dosyalarından metin tanıma |
| Regex | Metin içerisinde belirli ifadeleri tespit etme |
| Docker | Uygulamayı konteyner ortamında çalıştırma |

## 3. Uygulamanın Özellikleri

- PDF formatındaki CV dosyalarını okuma
- Metin tabanlı PDF dosyalarından içerik çıkarma
- Taranmış PDF dosyalarında OCR kullanma
- Türkçe ve İngilizce OCR desteği
- Altı temel yapısal kriteri kontrol etme
- Yapısal uygunluk yüzdesini hesaplama
- LinkedIn ve GitHub bağlantılarını tespit etme
- Ölçülebilir başarı ifadelerini kontrol etme
- CV geliştirme önerileri oluşturma
- Eksik veya bozuk PDF dosyalarında hata yönetimi
- Docker üzerinden çalıştırma

## 4. Uygulamanın Çalışma Mantığı

1. Kullanıcı terminal üzerinden PDF dosyasının yolunu girer.
2. Apache PDFBox ile dosyadan metin çıkarılmaya çalışılır.
3. Kullanılabilir metin bulunamazsa Tesseract OCR devreye girer.
4. Elde edilen metin belirlenen kurallara göre analiz edilir.
5. Analiz sonuçları terminal ekranında gösterilir.
6. Eksik kriterler için iyileştirme önerileri oluşturulur.

## 5. ATS Analiz Kriterleri

Uygulama CV içerisinde altı temel kriteri kontrol eder.

| Kriter | Kontrol |
|---|---|
| E-posta | E-posta adresinin bulunması |
| Telefon | Telefon numarasının bulunması |
| Eğitim | Eğitim bölümünün bulunması |
| Deneyim | İş deneyimi veya staj bölümünün bulunması |
| Yetkinlikler | Teknik beceriler bölümünün bulunması |
| Projeler | Projeler bölümünün bulunması |

### Yapısal Uygunluk Oranı

Uygulama, tespit edilen kriter sayısını toplam kriter sayısına bölerek yüzdelik sonuç oluşturur.

**Yapısal Uygunluk (%) = (Tespit Edilen Kriter / 6) × 100**

Örneğin:

- 6/6 kriter: %100
- 5/6 kriter: %83,3
- 4/6 kriter: %66,7

**Önemli:** Bu oran gerçek bir ATS puanı değildir. Yalnızca uygulamanın belirlediği altı yapısal kriterin karşılanma oranını gösterir.

## 6. Ek Analizler

### LinkedIn ve GitHub Kontrolü

CV içerisinde LinkedIn ve GitHub adreslerinin bulunup bulunmadığı kontrol edilir.

### Ölçülebilir Başarı Kontrolü

Deneyim ve proje bölümlerindeki sayısal sonuçlar ile başarı bildiren ifadeler incelenir.

Örnekler:

- Performansı %30 artırdım.
- 12 proje tamamladım.
- İşlem süresini %25 azalttım.

Bu kontrol kural tabanlıdır. Her başarı ifadesini tespit edemeyebilir.

## 7. Docker ile Çalıştırma

### Docker İmajını Oluşturma

Projenin ana klasöründe terminal açılarak aşağıdaki komut çalıştırılır:

```bash
docker build -t cv-ats-analyzer .
```

### Uygulamayı Başlatma

Windows PowerShell üzerinden:

```powershell
docker run -it --rm -v "C:\PDF_KLASORU:/data:ro" cv-ats-analyzer
```

`C:\PDF_KLASORU` yerine PDF dosyalarının bulunduğu klasör yazılmalıdır.

Program dosya yolunu istediğinde:

```text
/data/ornek-cv.pdf
```

girilir.

Ardından uygulama analiz raporunu terminal ekranında gösterir.

## 8. Örnek Analiz Sonucu

```text
=== ATS ANALIZ RAPORU ===
[OK] E-posta bulundu.
[OK] Telefon bulundu.
[OK] Egitim bulundu.
[OK] Deneyim bulundu.
[OK] Yetkinlikler bulundu.
[!!] Projeler tespit edilemedi.

=== ANALIZ OZETI ===
Tespit edilen kriter: 5/6
Yapisal uygunluk orani: %83.3
LinkedIn: Bulundu
GitHub: Bulundu
Olculebilir basari ifadesi: Bulunamadi

=== IYILESTIRME ONERILERI ===
- Deneyimlerinizi mumkunse olculebilir sonuclarla destekleyin.
- Projelerinizi ayri bir bolumde sunun.
```

## 9. Uygulamanın Sınırlamaları

- Analiz işlemleri kural tabanlıdır.
- OCR işlemi bazı karakterleri hatalı tanıyabilir.
- Bölüm tespiti belirli başlıklara dayanır.
- Ölçülebilir başarı kontrolünde hatalı veya eksik tespitler oluşabilir.
- Belirli bir iş ilanıyla CV eşleştirmesi yapılmaz.
- Gerçek ATS sistemlerinin değerlendirme algoritmaları kullanılmaz.

## 10. Projenin Amacı

Bu proje; Java programlama, PDF işleme, OCR entegrasyonu, düzenli ifadeler, hata yönetimi ve Docker teknolojilerinin birlikte kullanılmasını uygulamalı olarak öğrenmek amacıyla geliştirilmiştir.

Aynı zamanda özgeçmişlerin yapısal açıdan değerlendirilmesini ve geliştirilmesi gereken alanların belirlenmesini hedeflemektedir.