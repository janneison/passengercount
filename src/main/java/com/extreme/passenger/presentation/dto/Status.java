package com.extreme.passenger.presentation.dto;

import org.springframework.http.HttpStatus;

public enum Status {

    OK("OK", HttpStatus.OK),
    ERROR("ERROR", HttpStatus.INTERNAL_SERVER_ERROR),
    NOT_FOUND("NOT_FOUND", HttpStatus.NOT_FOUND),
    INVALID("INVALID", HttpStatus.UNPROCESSABLE_ENTITY),
    RECEIVED("RECEIVED", HttpStatus.ACCEPTED),
    DISCARDED("DISCARDED", HttpStatus.OK);


    private Status(String name, HttpStatus httpStatus) {
        this.name = name;   
        this.httpStatus = httpStatus;
    }

    private final HttpStatus httpStatus;

    private final String name;

    public String getName() {
        return name;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

}
