package com.extreme.passenger.presentation.mapper;


import org.springframework.stereotype.Component;

import com.extreme.passenger.domain.model.PassengerEvent;
import com.extreme.passenger.presentation.dto.PassengerEventIn;

@Component
public class PassengerEventMapper {

    public PassengerEvent toDomain(PassengerEventIn in) {
        if (in == null) return null;

        var b = PassengerEvent.builder();

        if (in.getIdVehicle() != null && !in.getIdVehicle().isBlank()) b.idVehicle(in.getIdVehicle().trim());
        if (in.getDoor1In() != null)    b.door1In(in.getDoor1In());
        if (in.getDoor1Out() != null)   b.door1Out(in.getDoor1Out());
        if (in.getDoor1Block() != null) b.door1Block(in.getDoor1Block());
        if (in.getDoor2In() != null)    b.door2In(in.getDoor2In());
        if (in.getDoor2Out() != null)   b.door2Out(in.getDoor2Out());
        if (in.getDoor2Block() != null) b.door2Block(in.getDoor2Block());
        if (in.getDoor3In() != null)    b.door3In(in.getDoor3In());
        if (in.getDoor3Out() != null)   b.door3Out(in.getDoor3Out());
        if (in.getDoor3Block() != null) b.door3Block(in.getDoor3Block());
        if (in.getCheckinTime() != null) b.checkinTime(in.getCheckinTime());
        if (in.getLatitude() != null)    b.latitude(in.getLatitude());
        if (in.getLongitude() != null)   b.longitude(in.getLongitude());

        return b.build();
    }

}
