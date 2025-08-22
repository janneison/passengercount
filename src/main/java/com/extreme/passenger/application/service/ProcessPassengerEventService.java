package com.extreme.passenger.application.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.extreme.passenger.domain.model.Accumulators;
import com.extreme.passenger.domain.model.PassengerEvent;
import com.extreme.passenger.domain.port.PassengerEventRepository;
import com.extreme.passenger.domain.port.PassengerEventRepository.PrevPoint;
import com.extreme.passenger.domain.port.PassengerEventRepository.RepoAcc;
import com.extreme.passenger.infrastructure.config.AppProperties;
import com.extreme.passenger.presentation.dto.PassengerEventOut;
import com.extreme.passenger.presentation.dto.Status;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessPassengerEventService {

    private final AppProperties props;
    private final PassengerEventRepository repository;

    private ZoneId zone() { return ZoneId.of(props.getTimezone()); }

    private Instant now() { return Instant.now(); }

    @Transactional
    public PassengerEventOut processSync(PassengerEvent event) {

        if (event == null) {
            return PassengerEventOut.builder()
                    .status(Status.INVALID)
                    .message("Evento nulo")
                    .data(null)
                    .build();
        }

        String messageId = String.valueOf(System.nanoTime());
        String logPrefix = "[vehicle=" + event.getIdVehicle() + "][eventId=" + messageId + "]";

        try {
            log.info("{} Evento recibido: {}", logPrefix, event);

            repository.lockVehicleRow(event.getIdVehicle());

            // 0) Verificar vehículo
            if (!repository.vehicleExists(event.getIdVehicle())) {
                log.warn("{} Vehículo no encontrado", logPrefix);
                return PassengerEventOut.builder()
                        .status(Status.NOT_FOUND)
                        .message("Vehículo no encontrado")
                        .data(event)
                        .build();
            }

            // 1) Acumulados totales de esta lectura (raw)
            Accumulators raw = Accumulators.builder()
                    .door1In(nz(event.getDoor1In()))
                    .door1Out(nz(event.getDoor1Out()))
                    .door1Block(nz(event.getDoor1Block()))
                    .door2In(nz(event.getDoor2In()))
                    .door2Out(nz(event.getDoor2Out()))
                    .door2Block(nz(event.getDoor2Block()))
                    .door3In(nz(event.getDoor3In()))
                    .door3Out(nz(event.getDoor3Out()))
                    .door3Block(nz(event.getDoor3Block()))
                    .build();
            raw.setTotalIn(raw.getDoor1In() + raw.getDoor2In() + raw.getDoor3In());
            raw.setTotalOut(raw.getDoor1Out() + raw.getDoor2Out() + raw.getDoor3Out());
            raw.setTotalBlock(raw.getDoor1Block() + raw.getDoor2Block() + raw.getDoor3Block());

            // 2) last_date y flag history
            Optional<Instant> lastDateOpt = repository.getLastDate(event.getIdVehicle());
            boolean hasHistory = repository.hasHistory(event.getIdVehicle());

            // 3) prev accum si aplica
            Accumulators prev = Accumulators.builder()
                    .totalIn(0).totalOut(0).totalBlock(0)
                    .door1In(0).door1Out(0).door1Block(0)
                    .door2In(0).door2Out(0).door2Block(0)
                    .build();
            Long prevProgramId = null;

            if (lastDateOpt.isPresent() && hasHistory) {
                Instant lastDate = lastDateOpt.get();
                Optional<RepoAcc> ra = repository.getLastAccumulators(event.getIdVehicle(), lastDate);
                if (ra.isPresent()) {
                    prevProgramId = ra.get().programId();
                    var a = ra.get().acc();
                    prev = Accumulators.builder()
                            .totalIn(a.getTotalIn()).totalOut(a.getTotalOut()).totalBlock(a.getTotalBlock())
                            .door1In(a.getDoor1In()).door1Out(a.getDoor1Out()).door1Block(a.getDoor1Block())
                            .door2In(a.getDoor2In()).door2Out(a.getDoor2Out()).door2Block(a.getDoor2Block())
                            .build();
                }
            }

            // 4) netos no negativos
            Accumulators net = Accumulators.builder()
                    .door1In(Math.max(raw.getDoor1In() - prev.getDoor1In(), 0))
                    .door1Out(Math.max(raw.getDoor1Out() - prev.getDoor1Out(), 0))
                    .door1Block(Math.max(raw.getDoor1Block() - prev.getDoor1Block(), 0))
                    .door2In(Math.max(raw.getDoor2In() - prev.getDoor2In(), 0))
                    .door2Out(Math.max(raw.getDoor2Out() - prev.getDoor2Out(), 0))
                    .door2Block(Math.max(raw.getDoor2Block() - prev.getDoor2Block(), 0))
                    .build();
            net.setTotalIn(Math.max(raw.getTotalIn() - prev.getTotalIn(), 0));
            net.setTotalOut(Math.max(raw.getTotalOut() - prev.getTotalOut(), 0));
            net.setTotalBlock(Math.max(raw.getTotalBlock() - prev.getTotalBlock(), 0));

            // 5) minutos desde last_date
            Double minutesDiff = null;
            if (lastDateOpt.isPresent()) {
                Instant last = lastDateOpt.get();
                ZonedDateTime lastZ = ZonedDateTime.ofInstant(last, zone());
                Duration delta = Duration.between(lastZ, ZonedDateTime.ofInstant(now(), zone()));
                minutesDiff = delta.getSeconds() / 60.0;
            }

            // 6) descarte por pico
            int tol = props.getPassengerCountTolerance();
            int minLimit = props.getTimeThresholdMinutes();
            boolean spike = anyGte(net, tol);
            boolean inWindow = minutesDiff != null && minutesDiff < minLimit;
            boolean excluded = props.getExcludedIds()
                    .stream().map(s -> s.toUpperCase(Locale.ROOT))
                    .anyMatch(s -> s.equals(event.getIdVehicle().toUpperCase(Locale.ROOT)));

            if (lastDateOpt.isPresent() && spike && inWindow && !excluded) {
                repository.insertDiscarded(event, net, raw, null, null);
                log.warn("{} Evento descartado por pico (net={} minDiff={} tol={} minLimit={})", logPrefix, net, minutesDiff, tol, minLimit);
                return PassengerEventOut.builder()
                        .status(Status.DISCARDED)
                        .message("Evento descartado por pico")
                        .data(event)
                        .build();
            }

            // 7) viaje actual
            Long programId = repository.getActiveProgramToday(event.getIdVehicle()).orElse(null);
            log.info("{} Viaje actual: {}", logPrefix, programId);

            // 8) punto anterior
            Long pointId = null;
            if (programId == null && lastDateOpt.isPresent() && hasHistory && prevProgramId != null) {
                Optional<PrevPoint> pp = repository.getLastBlankPointOfProgram(prevProgramId);
                if (pp.isPresent() && pp.get().blank()) {
                    programId = prevProgramId;
                    pointId = pp.get().pointId();
                    repository.updateProgvehiculosCounters(programId, net.getTotalIn(), net.getTotalOut(), net.getTotalBlock());
                    log.info("{} Punto anterior encontrado: programId={} pointId={}", logPrefix, programId, pointId);
                }
            }

            // 9) ajuste de fecha
            Instant eventTs = event.getCheckinTime();
            ZonedDateTime eventZ = ZonedDateTime.ofInstant(eventTs, zone());
            long days = Math.abs(Duration.between(eventZ, ZonedDateTime.ofInstant(now(), zone())).toDays());
            Instant currentDate = days > 1 ? now() : eventTs;

            // 10) insert final + update vehiculos
            repository.insertPassengerEvent(event, programId, pointId, net, raw);
            log.info("{} Evento insertado {}", logPrefix, event);
            repository.updateVehicleLastCount(event.getIdVehicle(), currentDate);
            log.info("{} Vehículo actualizado", logPrefix);

            return PassengerEventOut.builder()
                    .status(Status.OK)
                    .message("Evento procesado exitosamente")
                    .data(event)
                    .build();

        } catch (Exception e) {
            log.error("Error procesando evento", e);
            return PassengerEventOut.builder()
                    .status(Status.ERROR)
                    .message("Error interno del servidor: " + e.getMessage())
                    .data(event)
                    .build();
        }
    }

    // ---- helpers ----
    private static int nz(Integer v) { return v == null ? 0 : v; }

    private static boolean anyGte(Accumulators n, int tol) {
        return n.getTotalIn()  >= tol || n.getDoor1In()  >= tol || n.getDoor2In()  >= tol ||
               n.getTotalOut() >= tol || n.getDoor1Out() >= tol || n.getDoor2Out() >= tol;
    }

    @Async
    public CompletableFuture<PassengerEventOut> processAsync(PassengerEvent event) {
        return CompletableFuture.completedFuture(processSync(event));
    }
}