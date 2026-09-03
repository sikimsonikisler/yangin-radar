-- Sik calisan sorgular icin indeksler.
--
-- Bunlar olmadan asagidaki uc sorgu tam tablo taramasi yapiyordu. Kayit sayisi
-- azken fark edilmez; birkac bin fotografta belirgin sekilde yavaslar.

-- 1) Tekrar kullanilan kare kontrolu: PhotoEvidenceRepository.findBySha256
--    HER fotograf yuklemesinde calisir ve sahtecilik zincirinin parcasidir.
create index if not exists idx_photo_sha256 on photo_evidence (sha256);

-- 2) Capraz dogrulama kutusu: FireReportRepository.findNearby
--    HER yeni ihbarda calisir; yakindaki bagimsiz ihbarlari arar.
create index if not exists idx_fire_report_konum on fire_report (lat, lon);

-- 3) Zamana gore siralamalar: findTop200ByOrderByCreatedAtDesc,
--    findByStatusOrderByCreatedAtDesc ve findNearby'nin zaman filtresi.
create index if not exists idx_fire_report_created_at on fire_report (created_at);

-- 4) Suresi dolmus meydan okumalarin temizligi (yarim saatte bir calisir).
create index if not exists idx_capture_challenge_expires on capture_challenge (expires_at);

-- Not: photo_evidence.fire_report_id icin H2 yabanci anahtar kisitiyla birlikte
-- kendiliginden indeks olusturuyor; tekrar eklemeye gerek yok.
