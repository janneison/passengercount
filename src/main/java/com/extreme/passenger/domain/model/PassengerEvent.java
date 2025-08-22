package com.extreme.passenger.domain.model;


import lombok.Builder;
import lombok.Value;
import java.time.Instant;

/**
 * Represents a passenger event with details about vehicle doors and check-in time.
 */
@Value
@Builder
public class PassengerEvent {
    String idVehicle;
    int door1In;
    int door1Out;
    int door1Block;
    int door2In;
    int door2Out;
    int door2Block;
    int door3In;
    int door3Out;
    int door3Block;
    Instant checkinTime; // UTC
    double latitude;
    double longitude;

}