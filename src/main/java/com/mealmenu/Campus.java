package com.mealmenu;

/** Rutgers–New Brunswick campuses, used as starting points for distances. */
public enum Campus {
    COLLEGE_AVE("College Ave", 40.5025, -74.4515),
    BUSCH("Busch", 40.5235, -74.4583),
    LIVINGSTON("Livingston", 40.5240, -74.4365),
    COOK_DOUGLASS("Cook/Douglass", 40.4810, -74.4370);

    /** Menus and hours follow New Brunswick time even if the computer is set to another zone. */
    public static final java.time.ZoneId ZONE = java.time.ZoneId.of("America/New_York");

    private final String label;
    private final double lat;
    private final double lng;

    Campus(String label, double lat, double lng) {
        this.label = label;
        this.lat = lat;
        this.lng = lng;
    }

    public String label() {
        return label;
    }

    /** Straight-line distance in miles. */
    public double milesTo(double otherLat, double otherLng) {
        double r = 3958.8;
        double dLat = Math.toRadians(otherLat - lat);
        double dLng = Math.toRadians(otherLng - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(otherLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    @Override
    public String toString() {
        return label;
    }
}
