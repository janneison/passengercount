package com.extreme.passenger.infrastructure.persistence.jdbc;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.extreme.passenger.domain.model.Accumulators;
import com.extreme.passenger.domain.model.PassengerEvent;
import com.extreme.passenger.domain.port.PassengerEventRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PassengerEventJdbcRepository implements PassengerEventRepository {

    private final JdbcTemplate jdbc;

    // ---- SQLs ----
    private static final String SQL_VEHICLE_EXISTS =
            "SELECT EXISTS(SELECT 1 FROM vehiculos WHERE idvehiculo = ?)";

    private static final String SQL_LAST_DATE =
            "SELECT fecha_ultimo_conteo FROM vehiculos WHERE idvehiculo = ?";

    private static final String SQL_HAS_HISTORY =
            "SELECT EXISTS(SELECT 1 FROM conteo_pasajeros WHERE idvehiculo = ?)";

    private static final String SQL_LAST_ACCUM = """
        SELECT idprogramacion,
               acumulada_subida, acumulada_bajada, acumulada_bloqueo,
               acumulada_subida_puerta1, acumulada_bajada_puerta1, acumulada_bloqueo_puerta1,
               acumulada_subida_puerta2, acumulada_bajada_puerta2, acumulada_bloqueo_puerta2
        FROM conteo_pasajeros
        WHERE idvehiculo = ? AND fecha <= ?
        ORDER BY id DESC
        LIMIT 1
        """;

    private static final String SQL_ACTIVE_PROGRAM_TODAY = """
        SELECT idprogramacion
        FROM progvehiculos
        WHERE idvehiculo = ? AND activa = 'S' AND fechasalida::date = localtimestamp::date
        ORDER BY idprogramacion DESC LIMIT 1
        """;

    private static final String SQL_LAST_BLANK_POINT = """
        SELECT idpunto, orden,
               (cantidad_subida IS NULL OR cantidad_bajada IS NULL OR cantidad_bloqueo IS NULL) AS blank
        FROM rutascontrol
        WHERE idprogramacion = ?
        ORDER BY orden DESC
        LIMIT 1
        """;

    private static final String SQL_UPDATE_PROG_COUNTERS = """
        UPDATE progvehiculos
        SET numeropasajeros = COALESCE(numeropasajeros,0) + ?,
            numerobajadas   = COALESCE(numerobajadas,0)   + ?,
            numerobloqueos  = COALESCE(numerobloqueos,0)  + ?
        WHERE idprogramacion = ?
        """;

    private static final String SQL_INSERT_DISCARDED = """
        INSERT INTO conteo_pasajeros_descartados (
            fecha, idvehiculo,
            acumulada_subida, cantidad_subida,
            acumulada_bajada, cantidad_bajada,
            acumulada_bloqueo, cantidad_bloqueo,
            latitud, longitud, idprogramacion, idpunto,
            acumulada_subida_puerta1, acumulada_bajada_puerta1, acumulada_bloqueo_puerta1,
            cantidad_subida_puerta1, cantidad_bajada_puerta1, cantidad_bloqueo_puerta1,
            acumulada_subida_puerta2, acumulada_bajada_puerta2, acumulada_bloqueo_puerta2,
            cantidad_subida_puerta2, cantidad_bajada_puerta2, cantidad_bloqueo_puerta2
        ) VALUES (
            NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
            ?, ?, ?, ?, ?, ?,
            ?, ?, ?, ?, ?, ?
        )
        """;

    private static final String SQL_INSERT_MAIN = """
        INSERT INTO conteo_pasajeros (
            fecha, idvehiculo,
            acumulada_subida, cantidad_subida,
            acumulada_bajada, cantidad_bajada,
            acumulada_bloqueo, cantidad_bloqueo,
            latitud, longitud, idprogramacion, idpunto,
            acumulada_subida_puerta1, acumulada_bajada_puerta1, acumulada_bloqueo_puerta1,
            cantidad_subida_puerta1, cantidad_bajada_puerta1, cantidad_bloqueo_puerta1,
            acumulada_subida_puerta2, acumulada_bajada_puerta2, acumulada_bloqueo_puerta2,
            cantidad_subida_puerta2, cantidad_bajada_puerta2, cantidad_bloqueo_puerta2
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                  ?, ?, ?, ?, ?, ?,
                  ?, ?, ?, ?, ?, ?)
        """;

    private static final String SQL_UPDATE_LAST_COUNT =
            "UPDATE vehiculos SET fecha_ultimo_conteo = ? WHERE idvehiculo = ?";

    private static final String SQL_LOCK_VEHICLE =
        "SELECT idvehiculo FROM vehiculos WHERE idvehiculo = ? FOR UPDATE";

    @Override public void lockVehicleRow(String idVehicle) {
        // Toma un lock exclusivo sobre la fila del vehículo hasta el commit/rollback
        jdbc.queryForObject(SQL_LOCK_VEHICLE, String.class, idVehicle);
    }

    @Override public boolean vehicleExists(String idVehicle) {
        Boolean exists = jdbc.queryForObject(SQL_VEHICLE_EXISTS, Boolean.class, idVehicle);
        return Boolean.TRUE.equals(exists);
    }

    @Override public Optional<Instant> getLastDate(String idVehicle) {
        Timestamp ts = jdbc.queryForObject(SQL_LAST_DATE, Timestamp.class, idVehicle);
        return Optional.ofNullable(ts).map(Timestamp::toInstant);
    }

    @Override public boolean hasHistory(String idVehicle) {
        Boolean flag = jdbc.queryForObject(SQL_HAS_HISTORY, Boolean.class, idVehicle);
        return Boolean.TRUE.equals(flag);
    }

    @Override public Optional<RepoAcc> getLastAccumulators(String idVehicle, Instant until) {
        try {
            return jdbc.query(SQL_LAST_ACCUM,
                    ps -> { ps.setString(1, idVehicle); ps.setTimestamp(2, Timestamp.from(until)); },
                    rs -> rs.next()
                            ? Optional.of(new RepoAcc(rs.getObject("idprogramacion", Long.class), mapAcc(rs)))
                            : Optional.<RepoAcc>empty()
            );
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override public Optional<Long> getActiveProgramToday(String idVehicle) {
        try {
            Long id = jdbc.queryForObject(SQL_ACTIVE_PROGRAM_TODAY, Long.class, idVehicle);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override public Optional<PrevPoint> getLastBlankPointOfProgram(Long programId) {
        if (programId == null) return Optional.empty();
        try {
            return jdbc.query(SQL_LAST_BLANK_POINT, ps -> ps.setLong(1, programId), rs ->
                    rs.next()
                        ? Optional.of(new PrevPoint(
                            rs.getObject("idpunto", Long.class),
                            rs.getObject("orden", Integer.class),
                            rs.getBoolean("blank")))
                        : Optional.<PrevPoint>empty()
            );
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override public void updateProgvehiculosCounters(Long programId, int in, int out, int block) {
        if (programId == null) return;
        jdbc.update(SQL_UPDATE_PROG_COUNTERS, in, out, block, programId);
    }

    @Override public void insertDiscarded(PassengerEvent ev, Accumulators net, Accumulators raw, Long programId, Long pointId) {
        jdbc.update(SQL_INSERT_DISCARDED,
                ev.getIdVehicle(),
                raw.getTotalIn(),  net.getTotalIn(),
                raw.getTotalOut(), net.getTotalOut(),
                raw.getTotalBlock(), net.getTotalBlock(),
                ev.getLatitude(), ev.getLongitude(),
                programId, pointId,
                raw.getDoor1In(), raw.getDoor1Out(), raw.getDoor1Block(),
                net.getDoor1In(), net.getDoor1Out(), net.getDoor1Block(),
                raw.getDoor2In(), raw.getDoor2Out(), raw.getDoor2Block(),
                net.getDoor2In(), net.getDoor2Out(), net.getDoor2Block()
        );
    }

    @Override public void insertPassengerEvent(PassengerEvent ev, Long programId, Long pointId,
                                               Accumulators net, Accumulators raw) {
        jdbc.update(SQL_INSERT_MAIN,
                Timestamp.from(ev.getCheckinTime()), ev.getIdVehicle(),
                raw.getTotalIn(),  net.getTotalIn(),
                raw.getTotalOut(), net.getTotalOut(),
                raw.getTotalBlock(), net.getTotalBlock(),
                ev.getLatitude(), ev.getLongitude(), programId, pointId,
                raw.getDoor1In(), raw.getDoor1Out(), raw.getDoor1Block(),
                net.getDoor1In(), net.getDoor1Out(), net.getDoor1Block(),
                raw.getDoor2In(), raw.getDoor2Out(), raw.getDoor2Block(),
                net.getDoor2In(), net.getDoor2Out(), net.getDoor2Block()
        );
    }

    @Override public void updateVehicleLastCount(String idVehicle, Instant when) {
        jdbc.update(SQL_UPDATE_LAST_COUNT, Timestamp.from(when), idVehicle);
    }

    // ---- helpers ----
    private static Accumulators mapAcc(ResultSet rs) throws SQLException {
        return Accumulators.builder()
                .totalIn(nz(rs, "acumulada_subida"))
                .totalOut(nz(rs, "acumulada_bajada"))
                .totalBlock(nz(rs, "acumulada_bloqueo"))
                .door1In(nz(rs, "acumulada_subida_puerta1"))
                .door1Out(nz(rs, "acumulada_bajada_puerta1"))
                .door1Block(nz(rs, "acumulada_bloqueo_puerta1"))
                .door2In(nz(rs, "acumulada_subida_puerta2"))
                .door2Out(nz(rs, "acumulada_bajada_puerta2"))
                .door2Block(nz(rs, "acumulada_bloqueo_puerta2"))
                .build();
    }
    private static int nz(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? 0 : v;
    }
}