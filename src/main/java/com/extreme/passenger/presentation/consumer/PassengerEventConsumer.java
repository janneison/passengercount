package com.extreme.passenger.presentation.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.extreme.passenger.application.service.ProcessPassengerEventService;
import com.extreme.passenger.presentation.dto.PassengerEventIn;
import com.extreme.passenger.presentation.mapper.PassengerEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PassengerEventConsumer {

    private final ObjectMapper json;
    private final PassengerEventMapper mapper;
    private final ProcessPassengerEventService service;

    @KafkaListener(topics = "${app.kafka.passenger-topic}", concurrency = "${KAFKA_CONCURRENCY:1}", autoStartup = "${app.kafka.consumer-enabled}")
    public void onMessage(String payload, Acknowledgment ack) {
        if (payload == null || payload.isBlank()) {
            log.debug("Kafka: payload vacío → ack & skip");
            ack.acknowledge();
            return;
        }
        log.debug("Kafka: recibido payload: {}", payload);
        try {
            PassengerEventIn in = json.readValue(payload, PassengerEventIn.class);
            log.debug("Kafka: recibido evento {}", in);
            service.process(mapper.toDomain(in));
        } catch (Exception e) {
            log.error("Kafka: error, se ackea igual (at-most-once)", e);
        } finally {
            ack.acknowledge();
        }
    }
}

