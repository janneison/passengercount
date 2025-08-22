package com.extreme.passenger.infrastructure.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter @Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private List<String> excludedIds;
    private int passengerCountTolerance;
    private int timeThresholdMinutes;
    private String timezone;

}
