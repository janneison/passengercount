package com.extreme.passenger.domain.port;

import java.time.Instant;
import java.util.Optional;

import com.extreme.passenger.domain.model.Accumulators;
import com.extreme.passenger.domain.model.PassengerEvent;

public interface PassengerEventRepository {
    boolean vehicleExists(String idVehicle);

    Optional<Instant> getLastDate(String idVehicle);
    boolean hasHistory(String idVehicle);
    Optional<RepoAcc> getLastAccumulators(String idVehicle, Instant until);

    Optional<Long> getActiveProgramToday(String idVehicle); // progvehiculos activa hoy
    Optional<PrevPoint> getLastBlankPointOfProgram(Long programId); // rutascontrol

    void updateProgvehiculosCounters(Long programId, int in, int out, int block);

    void insertDiscarded(PassengerEvent ev, Accumulators accNet, Accumulators accRaw, Long programId, Long pointId);

    void insertPassengerEvent(PassengerEvent ev, Long programId, Long pointId,
                              Accumulators accNet, Accumulators accRaw);

    void updateVehicleLastCount(String idVehicle, Instant when);

    public void lockVehicleRow(String idVehicle);

    // Tipos auxiliares
    record RepoAcc(Long programId, Accumulators acc) {}
    record PrevPoint(Long pointId, Integer order, boolean blank) {}
}