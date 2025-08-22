# Proceso de Ingesta y Conteo de Pasajeros — Documento Técnico

Este documento describe **a detalle** el flujo de negocio y técnico del servicio de *Passenger Event Processing* sin centrarse en el código, sino en **qué hace** y **por qué**. Aplica igual para eventos recibidos por **REST** o por **Kafka** (únicamente consumidor).

---

## 1) Resumen del flujo (alto nivel)

1. **Entrada** (REST/Kafka) recibe un `PassengerEvent`.
2. **Validación inicial**: evento nulo → `INVALID`.
3. **Transacción única**: el Service abre transacción y **bloquea** la fila del vehículo (`SELECT ... FOR UPDATE`) para **serializar por vehículo**.
4. **Verificación de vehículo**: si no existe → `NOT_FOUND`.
5. **Contexto previo**:
   - Última fecha de conteo en `vehiculos.fecha_ultimo_conteo`.
   - ¿Hay históricos en `conteo_pasajeros`? Si sí y hay última fecha, se consultan **acumulados previos** (≤ `lastDate`) y `prevProgramId`.
6. **Cálculo**:
   - `raw`: sumas totales y por puerta del evento recibido.
   - `net`: `max(raw - prev, 0)` por cada métrica (no negativos).
   - `minutesDiff`: minutos desde `lastDate` (si existe).
7. **Regla de descarte por pico**:
   - Si `net` supera **tolerancia** y está dentro de **ventana de minutos** y el vehículo **no está excluido**, se inserta en `conteo_pasajeros_descartados` y termina con `DISCARDED`.
8. **Contexto operativo (viaje/punto)**:
   - Busca **programación activa hoy** en `progvehiculos`.
   - Si no hay, intenta **reusar** la programación previa (`prevProgramId`) si el **último punto** (`rutascontrol`) está **en blanco**; de ser así, actualiza contadores de `progvehiculos` con los **netos**.
9. **Normalización de fecha**:
   - Si el `eventTime` difiere más de **1 día** respecto a `now`, se usa `now`; en caso contrario, se usa `eventTime` normalizado a zona `AppProperties.timezone`.
10. **Persistencia final**:
    - Inserta en `conteo_pasajeros` **tanto acumulados raw como netos**, más `lat/long` (0.0 si no llegaron), `programId`/`pointId`.
    - Actualiza `vehiculos.fecha_ultimo_conteo`.
11. **Commit**: se cierra la transacción; el lock del vehículo se libera.
12. **Respuesta**: `OK` | `DISCARDED` | `NOT_FOUND` | `INVALID` | `ERROR` (con mensaje y eco del evento).

---

## 2) Entradas, alias y supuestos de datos

- **Modelo de entrada**: `PassengerEventIn` acepta alias comunes (e.g., `vehicleID`, `idvehicle`, `doorIn1`, `door1_in`, etc.).
- **Tiempos**: `checkin_time` en formato `yyyy-MM-dd HH:mm:ss` (UTC por defecto). Se normaliza a `AppProperties.timezone`.
- **Lat/Lon opcionales**: si **no llegan**, se insertan como **`0.0 / 0.0`** y no bloquean el proceso.
- **Acumulados**: los valores `door*_in/out/block` del evento son **acumulados** reportados por el dispositivo; el sistema calcula **deltas netos** contra los acumulados previos.

---

## 3) Transacción, concurrencia y lock por vehículo

- El Service ejecuta el procesamiento en **una sola transacción**.
- Antes de leer/modificar datos del vehículo, llama a `SELECT idvehiculo FROM vehiculos WHERE idvehiculo = ? FOR UPDATE`.
- Efecto:
  - **Serializa** el procesamiento **por vehículo** (dos eventos del mismo vehículo no se pisan).
  - El lock se **libera al commit/rollback** (fin de la transacción).
- Implicaciones:
  - Podemos escalar instancias y recibir eventos concurrentes de **vehículos distintos** sin conflicto.
  - Si llegan muchos eventos del **mismo vehículo** a la vez, **hacen cola** sobre el lock (observado ~1 evento/seg a modo de referencia en pruebas).

---

## 4) Cálculo de `raw` y `net` (no negativos)

- `raw` = sumas puerta1..3 (in/out/block) y totales.
- `prev` = acumulados previos (si hay); si no, todos **0**.
- `net` = `max(raw - prev, 0)` en cada campo: evita negativos ante reinicios de contador/dispositivos erráticos.
- Beneficio: eventos duplicados o con valores iguales al previo resultan en `net = 0` → **idempotencia práctica** (no suma doble).

---

## 5) Descarte por pico (spike guard)

Se evita **falsos picos** comparando:
- `tolerancia`: umbral de cualquiera de los `net` (total y/o por puertas relevantes).
- `time_threshold_minutes`: ventana temporal desde `lastDate`.
- `excludedIds`: lista de vehículos **exentos** de esta regla.

**Condición de descarte** (y acción):

(lastDate existe) AND (algún net >= tolerancia) AND (minutesDiff < time_threshold_minutes) AND (vehículo NO excluido)
→ INSERT en conteo_pasajeros_descartados y Status=DISCARDED

Mostrar siempre los detalles

---

## 6) Asociación con programación y punto

- **Programación activa hoy** (`progvehiculos.activa='S' AND fechasalida::date=today`): si existe, se usa su `idprogramacion`.
- Si **no hay** programación activa **pero hay histórico**, se busca el **último punto** de `prevProgramId` en `rutascontrol` y se verifica si está **en blanco** (algún campo `cantidad_* IS NULL`).  
  - Si está en blanco: se **reasigna** `programId = prevProgramId`, se toma ese `pointId` y se **actualizan** contadores de `progvehiculos` con los **netos**.
  - Si no está en blanco: se inserta el evento **sin programId/punto** (`NULLs`).

---

## 7) Normalización de fecha del evento

- Se compara `eventTime` vs `now` (ambos en `AppProperties.timezone`).
- Si la diferencia absoluta en días es **> 1**, se usa `now` como `currentDate`.  
  Razonamiento: evita almacenar eventos con fechas **muy desfasadas** respecto al último conteo.
- En caso contrario, se usa `eventTime` normalizado.

---

## 8) Inserciones/actualizaciones finales

- `conteo_pasajeros`:
  - Guarda **acumulados raw**, **cantidades netas**, `lat/long`, `programId` y `pointId` (si aplica).
- `vehiculos`:
  - Actualiza `fecha_ultimo_conteo = currentDate`.
- Si hubo **descarte**, se inserta en `conteo_pasajeros_descartados` con las mismas convenciones de `raw`/`net`.

---

## 9) Estados de salida (`PassengerEventOut.status`)

- `OK` — Evento procesado y persistido con éxito.
- `DISCARDED` — Descartado por regla de pico (se registró en tabla de descartados).
- `NOT_FOUND` — Vehículo inexistente.
- `INVALID` — Evento nulo o inválido.
- `ERROR` — Error inesperado (ex. base de datos).

La respuesta incluye **`message`** y eco en **`data`** del evento recibido (cuando aplica).

---

## 10) Kafka vs REST

- **Kafka (solo consumidor)**:
  - Reconocimiento manual **`ack` en `finally`** → *at-most-once* (evitamos reprocesar por fallos temporales).
  - Recomendado **`ErrorHandlingDeserializer`** para ignorar/aislar mensajes mal formados.
  - **Gating por entorno**: se puede desactivar el listener con una variable (ej. `KAFKA_ENABLED=false`).

- **REST**:
  - Síncrono: útil para pruebas/control puntual desde JMeter/Postman.
  - Llama internamente el mismo `processSync`.

---

## 11) Configuración relevante

- **Zona horaria**: `AppProperties.timezone` (ej. `UTC` o `America/Bogota`).
- **Tolerancia**: `AppProperties.passengerCountTolerance` (picos).
- **Ventana**: `AppProperties.timeThresholdMinutes` (minutos desde último conteo).
- **Excluidos**: `AppProperties.excludedIds` (lista desde entorno/`.env`).  
- **DB**: `spring.datasource.*` (PostgreSQL).
- **Kafka**: `spring.kafka.bootstrap-servers`, `group-id`, `topic` (`app.kafka.passenger-topic`).
- **.env**: soportado vía `spring.config.import=optional:file:.env[.properties]`.

---

## 12) Idempotencia y orden

- **Idempotencia práctica**: si `raw` no aumenta, `net` da 0 ⇒ no duplica.
- **Orden por vehículo**: garantizado por el **lock** a nivel de fila del vehículo en la transacción.  
  Procesos paralelos **de distintos vehículos** no se bloquean entre sí.

---

## 13) Pruebas recomendadas (JMeter)

- **Counters** (`__counter(TRUE)`) para `doorIn1`, `doorOut1`, etc., partiendo de un **offset** (ej. `440120 + ${DOOR1_IN_COUNTER}`).
- **Fecha actual**: `${__time(yyyy-MM-dd HH:mm:ss,)}` para `date`.
- **Concurrencia**: empezar con 1 req/s y aumentar; observar:
  - Secuencialidad por `eventId` en logs.
  - Ausencia de `deadlocks`/timeouts.
  - Inserciones correctas y sin negativos en `net`.
- **Kafka**: probar con partición por `vehicleID` para mejor orden natural (si aplica), aun así el **lock** lo garantiza del lado del servicio.

---

## 14) Operación y monitoreo

- **Logs enriquecidos**: `[vehicle=<ID>][eventId=<nanos>]` permiten trazar un evento de punta a punta.
- **Métricas sugeridas** (futuro):
  - Contador de `OK`, `DISCARDED`, `NOT_FOUND`, `ERROR`.
  - Latencia por evento.
  - Contención por lock (tiempo esperando `FOR UPDATE`).

---

## 15) Preguntas frecuentes (FAQ)

**Q: ¿Qué pasa si el vehículo no tiene programación activa?**  
A: Se procesa el evento; si el último punto de la programación previa está “en blanco”, se **reasigna**; si no, se inserta **sin `programId/pointId`**.

**Q: ¿Y si no llega lat/long?**  
A: Se guardan como `0.0 / 0.0` para no bloquear la persistencia.

**Q: ¿Cómo se evita doble conteo?**  
A: Con `net = max(raw - prev, 0)`; duplicados generan netos cero.

**Q: ¿Cuándo se suelta el lock?**  
A: Al finalizar la transacción (commit/rollback).

**Q: ¿Por qué descartar por pico?**  
A: Para filtrar lecturas anómalas/ruidosas en ventanas cortas (configurable).

---

## 16) Consideraciones de diseño

- **Transacción única** = atomicidad de todo el flujo.
- **Lock por vehículo** = consistencia e integridad por dispositivo.
- **Regla de pico** = resiliencia a valores espurios.
- **Normalización temporal** = coherencia frente a eventos muy desfasados.
- **Cálculo por deltas** = idempotencia y sumas consistentes.

---