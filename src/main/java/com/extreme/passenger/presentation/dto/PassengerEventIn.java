package com.extreme.passenger.presentation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = PassengerEventIn.PassengerEventInBuilder.class)
public class PassengerEventIn {

    // Nombre canónico de salida "vehicleID"; acepta variantes
    @JsonProperty("vehicleID")
    @JsonAlias({"idvehicle", "idVehicle", "vehicle_id"})
    private String idVehicle;

    @JsonProperty("door1_in")
    @JsonAlias({"doorIn1", "door1In", "door_1_in"})
    private Integer door1In;

    @JsonProperty("door1_out")
    @JsonAlias({"doorOut1", "door1Out", "door_1_out"})
    private Integer door1Out;

    @JsonProperty("door1_block")
    @JsonAlias({"doorBlock1", "door1Block", "door_1_block"})
    private Integer door1Block;

    @JsonProperty("door2_in")
    @JsonAlias({"doorIn2", "door2In", "door_2_in"})
    private Integer door2In;

    @JsonProperty("door2_out")
    @JsonAlias({"doorOut2", "door2Out", "door_2_out"})
    private Integer door2Out;

    @JsonProperty("door2_block")
    @JsonAlias({"doorBlock2", "door2Block", "door_2_block"})
    private Integer door2Block;

    @JsonProperty("door3_in")
    @JsonAlias({"doorIn3", "door3In", "door_3_in"})
    private Integer door3In;

    @JsonProperty("door3_out")
    @JsonAlias({"doorOut3", "door3Out", "door_3_out"})
    private Integer door3Out;

    @JsonProperty("door3_block")
    @JsonAlias({"doorBlock3", "door3Block", "door_3_block"})
    private Integer door3Block;

    @JsonProperty("checkin_time")
    @JsonAlias({"date", "timestamp", "checkinTime", "datetime"})
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Instant checkinTime;

    @JsonProperty("latitude")
    @JsonAlias({"lat", "Latitude"})
    private Double latitude;

    @JsonProperty("longitude")
    @JsonAlias({"lng", "lon", "Longitude"})
    private Double longitude;

    @JsonPOJOBuilder(withPrefix = "")
    public static class PassengerEventInBuilder {}
}
