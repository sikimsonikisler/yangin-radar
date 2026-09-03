package com.yanginradar.config;

/** Basit kuresel mesafe hesaplari. */
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6_371_000d;

    private GeoUtils() {
    }

    /** Iki nokta arasi Haversine mesafesi, metre. */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1d, Math.sqrt(a)));
    }

    /** Verilen metre yaricapinin yaklasik enlem karsiligi (derece). */
    public static double latDelta(double meters) {
        return meters / 111_320d;
    }

    /** Verilen metre yaricapinin bu enlemdeki yaklasik boylam karsiligi (derece). */
    public static double lonDelta(double meters, double atLat) {
        double scale = Math.cos(Math.toRadians(atLat));
        if (scale < 0.01) {
            scale = 0.01;
        }
        return meters / (111_320d * scale);
    }

    /** Nokta Turkiye'yi kapsayan kaba kutunun icinde mi? */
    public static boolean insideTurkiye(double lat, double lon) {
        return lat >= 35.5 && lat <= 42.5 && lon >= 25.0 && lon <= 45.5;
    }
}
