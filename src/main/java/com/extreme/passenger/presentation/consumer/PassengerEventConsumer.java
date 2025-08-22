package com.extreme.passenger.presentation.consumer;

import java.util.EnumSet;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.extreme.passenger.application.service.ProcessPassengerEventService;
import com.extreme.passenger.presentation.dto.PassengerEventIn;
import com.extreme.passenger.presentation.dto.PassengerEventOut;
import com.extreme.passenger.presentation.dto.Status;
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

    private static final Set<Status> NON_RETRYABLE = 
    EnumSet.of(Status.OK, Status.DISCARDED, Status.NOT_FOUND, Status.INVALID);

    @KafkaListener(
        topics = "${app.kafka.passenger-topic}",
        concurrency = "${KAFKA_CONCURRENCY:1}",
        autoStartup = "${app.kafka.consumer-enabled}"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        final String payload = record.value();
        final String meta = String.format("[tp=%s-%d@%d key=%s]",
                record.topic(), record.partition(), record.offset(), record.key());

        // 1) Descarta ruidos (payload vacío) → ack & skip
        if (payload == null || payload.isBlank()) {
            log.debug("Kafka {}: payload vacío → ack & skip", meta);
            ack.acknowledge();
            return;
        }

        // 2) Deserialización (si falla NO se puede reprocesar) → ack & skip
        final PassengerEventIn in;
        try {
            in = json.readValue(payload, PassengerEventIn.class);
        } catch (Exception e) {
            log.error("Kafka {}: error deserializando payload → ack & skip", meta, e);
            ack.acknowledge();
            return;
        }

        // 3) Proceso de negocio: si falla (BD down, etc) NO ack → retry por el contenedor
        try {
            PassengerEventOut out = service.processSync(mapper.toDomain(in));
            if (NON_RETRYABLE.contains(out.getStatus())) {
                log.info("Kafka {}: procesado status='{}' → ack", meta, out.getStatus());
                ack.acknowledge();
            } else {
                    // Cualquier otro status lo tratamos como recuperable → NO ack
                    log.warn("Kafka {}: status recuperable='{}' → NO ack (se reintentará)", meta, out.getStatus());
            }
        } catch (Exception e) {
            // Error recuperable (BD, timeouts, etc.) → NO ack
            log.error("Kafka {}: error procesando (recuperable) → NO ack (retry)", meta, e);
        }
    }
}
