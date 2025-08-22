package com.extreme.passenger.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Accumulators {
    private int totalIn;
    private int totalOut;
    private int totalBlock;
    private int door1In;
    private int door1Out;
    private int door1Block;
    private int door2In;
    private int door2Out;
    private int door2Block;
    private int door3In;
    private int door3Out;
    private int door3Block;
}