package com.extreme.passenger.presentation.dto;

import com.extreme.passenger.domain.model.PassengerEvent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PassengerEventOut {

    private PassengerEvent data;
    private Status status;
    private String message;
    
}
