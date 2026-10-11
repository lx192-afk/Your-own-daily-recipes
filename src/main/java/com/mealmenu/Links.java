package com.mealmenu;

import javafx.application.HostServices;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Opens web links and map directions in the system browser. */
public final class Links {

    private static HostServices hostServices;

    private Links() {
    }

    static void init(HostServices services) {
        hostServices = services;
    }

    public static void open(String url) {
        if (hostServices != null && url != null && !url.isBlank()) {
            hostServices.showDocument(url);
        }
    }

    /** Google Maps directions to a place; the name + address gives Maps a precise match. */
    public static void directions(String name, String address, double lat, double lng) {
        String destination = address == null || address.isBlank()
                ? lat + "," + lng
                : name + ", " + address;
        open("https://www.google.com/maps/dir/?api=1&destination="
                + URLEncoder.encode(destination, StandardCharsets.UTF_8));
    }
}
