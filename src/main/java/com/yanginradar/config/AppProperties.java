package com.yanginradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml icindeki {@code yanginradar.*} ayarlari. */
@ConfigurationProperties(prefix = "yanginradar")
public class AppProperties {

    private final Storage storage = new Storage();
    private final Photo photo = new Photo();
    private final Firms firms = new Firms();
    private final Moderation moderation = new Moderation();
    private final Attest attest = new Attest();

    public Storage getStorage() {
        return storage;
    }

    public Photo getPhoto() {
        return photo;
    }

    public Firms getFirms() {
        return firms;
    }

    public Moderation getModeration() {
        return moderation;
    }

    public Attest getAttest() {
        return attest;
    }

    public static class Storage {
        /** Yuklenen fotograflarin diskteki kok dizini. */
        private String uploadDir = "./data/uploads";

        public String getUploadDir() {
            return uploadDir;
        }

        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }
    }

    public static class Photo {
        /** Kabul edilen en buyuk dosya boyutu (byte). */
        private long maxSizeBytes = 12L * 1024 * 1024;

        /** EXIF konumu ihbar noktasindan bu kadar metreden uzaksa fotograf supheli sayilir. */
        private double maxDistanceMeters = 3000;

        /** Fotograf bu dakikadan eskiyse supheli sayilir (varsayilan 6 saat). */
        private long maxAgeMinutes = 360;

        /**
         * EXIF DateTimeOriginal saat dilimi tasimaz - kameranin yerel saatidir.
         * Fotografta OffsetTimeOriginal ya da GPS zamani yoksa bu saat dilimi varsayilir.
         */
        private String assumedZone = "Europe/Istanbul";

        /** Cihaz saatlerindeki kaymaya taninan pay (dakika). */
        private long clockSkewMinutes = 30;

        public long getMaxSizeBytes() {
            return maxSizeBytes;
        }

        public void setMaxSizeBytes(long maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
        }

        public double getMaxDistanceMeters() {
            return maxDistanceMeters;
        }

        public void setMaxDistanceMeters(double maxDistanceMeters) {
            this.maxDistanceMeters = maxDistanceMeters;
        }

        public long getMaxAgeMinutes() {
            return maxAgeMinutes;
        }

        public void setMaxAgeMinutes(long maxAgeMinutes) {
            this.maxAgeMinutes = maxAgeMinutes;
        }

        public String getAssumedZone() {
            return assumedZone;
        }

        public void setAssumedZone(String assumedZone) {
            this.assumedZone = assumedZone;
        }

        public long getClockSkewMinutes() {
            return clockSkewMinutes;
        }

        public void setClockSkewMinutes(long clockSkewMinutes) {
            this.clockSkewMinutes = clockSkewMinutes;
        }
    }

    public static class Firms {
        /** NASA FIRMS MAP_KEY. Bos ise uydu akisi kapalidir, demo verisi kullanilir. */
        private String mapKey = "";

        private String baseUrl = "https://firms.modaps.eosdis.nasa.gov/api/area/csv";

        /** VIIRS_SNPP_NRT, VIIRS_NOAA20_NRT, MODIS_NRT ... */
        private String dataset = "VIIRS_SNPP_NRT";

        /** Kac gunluk veri cekilecek (FIRMS 1-10 arasi kabul eder). */
        private int dayRange = 1;

        /** Turkiye kutusu: minLon,minLat,maxLon,maxLat */
        private String area = "25.5,35.7,45.0,42.3";

        private boolean enabled = true;

        public String getMapKey() {
            return mapKey;
        }

        public void setMapKey(String mapKey) {
            this.mapKey = mapKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getDataset() {
            return dataset;
        }

        public void setDataset(String dataset) {
            this.dataset = dataset;
        }

        public int getDayRange() {
            return dayRange;
        }

        public void setDayRange(int dayRange) {
            this.dayRange = dayRange;
        }

        public String getArea() {
            return area;
        }

        public void setArea(String area) {
            this.area = area;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Apple App Attest ayarlari. */
    public static class Attest {
        /** Kapaliysa imza istenmez; fotograflar yalnizca EXIF ile degerlendirilir. */
        private boolean enabled = true;

        /** Apple Developer hesabinizin Team ID'si (10 karakter). */
        private String teamId = "";

        /** Uygulamanin bundle kimligi. */
        private String bundleId = "com.yanginradar.app";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTeamId() {
            return teamId;
        }

        public void setTeamId(String teamId) {
            this.teamId = teamId;
        }

        public String getBundleId() {
            return bundleId;
        }

        public void setBundleId(String bundleId) {
            this.bundleId = bundleId;
        }
    }

    public static class Moderation {
        /** Bu guven puanini gecen ihbarlar otomatik DOGRULANDI olur. */
        private int autoVerifyThreshold = 75;

        /** Yetkili panelindeki islemler icin gereken basit paylasilan anahtar. */
        private String adminToken = "degistir-beni";

        /** Ayni noktaya bu yaricap icindeki (metre) ihbarlar birbirini destekler sayilir. */
        private double corroborationRadiusMeters = 2500;

        /** Capraz dogrulamada bakilan zaman penceresi (dakika). */
        private long corroborationWindowMinutes = 180;

        public int getAutoVerifyThreshold() {
            return autoVerifyThreshold;
        }

        public void setAutoVerifyThreshold(int autoVerifyThreshold) {
            this.autoVerifyThreshold = autoVerifyThreshold;
        }

        public String getAdminToken() {
            return adminToken;
        }

        public void setAdminToken(String adminToken) {
            this.adminToken = adminToken;
        }

        public double getCorroborationRadiusMeters() {
            return corroborationRadiusMeters;
        }

        public void setCorroborationRadiusMeters(double corroborationRadiusMeters) {
            this.corroborationRadiusMeters = corroborationRadiusMeters;
        }

        public long getCorroborationWindowMinutes() {
            return corroborationWindowMinutes;
        }

        public void setCorroborationWindowMinutes(long corroborationWindowMinutes) {
            this.corroborationWindowMinutes = corroborationWindowMinutes;
        }
    }
}
