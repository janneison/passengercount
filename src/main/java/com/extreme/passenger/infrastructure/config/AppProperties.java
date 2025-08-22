package com.extreme.passenger.infrastructure.config;

import java.util.List;

public class AppProperties {

    public static List<String> excludedIds = List.of("COOCHOFAL250");

    public static int passengerCountTolerance = 150; // tolerancia
    public static int timeThresholdMinutes = 30; // minutos límite
    public static String timezone = "UTC";

}
