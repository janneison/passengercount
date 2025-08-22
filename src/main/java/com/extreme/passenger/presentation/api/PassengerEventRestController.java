package com.extreme.passenger.presentation.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.extreme.passenger.application.service.ProcessPassengerEventService;
import com.extreme.passenger.domain.model.PassengerEvent;
import com.extreme.passenger.presentation.dto.PassengerEventIn;
import com.extreme.passenger.presentation.dto.PassengerEventOut;
import com.extreme.passenger.presentation.mapper.PassengerEventMapper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController("passenger-events")
public class PassengerEventRestController {

    private final PassengerEventMapper mapper;
    private final ProcessPassengerEventService service;

    @PostMapping("sync/process-event")
    public ResponseEntity<PassengerEventOut> handleSyncIngest(@RequestBody PassengerEventIn in) {
        try {
            PassengerEvent event = mapper.toDomain(in);
            PassengerEventOut passengerEventOut = service.process(event).join();
            return ResponseEntity.status(HttpStatus.OK).body(passengerEventOut);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PassengerEventOut.builder()
                .data(null)
                .status("ERROR")
                .message("Failed to process event: " + e.getMessage())
                .build()
            );
        }
    }

    @PostMapping("async/process-event")
    public ResponseEntity<PassengerEventOut> handleAsyncIngest(@RequestBody PassengerEventIn in) {
        try {
            PassengerEvent event = mapper.toDomain(in);
            service.process(event);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(PassengerEventOut.builder()
                .data(event)
                .status("RECEIVED")
                .message("Event is being processed asynchronously")
                .build()
            );
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PassengerEventOut.builder()
                .data(null)
                .status("ERROR")
                .message("Failed to process event: " + e.getMessage())
                .build()
            );
        }
    }

}