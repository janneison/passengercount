# Passenger Count Service

Servicio backend para la **gestión y procesamiento de eventos de pasajeros en vehículos**.  
Permite recibir en tiempo real información de conteo (entradas/salidas por puerta, bloqueos, ubicación, timestamp) y mantener la consistencia de los datos por vehículo y viaje.

---

## Índice
1. [Arquitectura](#arquitectura)
2. [Tecnologías](#tecnologías)
3. [Endpoints](#endpoints)
4. [Flujo de Procesamiento](#flujo-de-procesamiento)
5. [Instalación y Despliegue](#instalación-y-despliegue)
6. [Pruebas con JMeter](#pruebas-con-jmeter)
7. [Logs y Debugging](#logs-y-debugging)

---

## Arquitectura

- **Spring Boot REST API** expuesta bajo `/api/v1/passenger-events`.
- **Control de concurrencia** mediante locks a nivel de fila para asegurar que cada vehículo procese sus eventos de forma **secuencial y consistente**.
- **Logs enriquecidos** con `vehicleId` y `messageId` para trazabilidad.

---

## Tecnologías

- **Java 21**
- **Spring Boot 3**
- **Spring Web** (REST)
- **Spring Data JPA**
- **PostgreSQL**
- **Docker & Docker Compose**
- **JMeter** (para pruebas de carga)

---

## Endpoints

### `POST /api/v1/passenger-events/sync/process-event`

Procesa un evento de pasajeros de forma síncrona.

#### Request (JSON)

```json
{
  "vehicleID": "EMBUSA50",
  "doorIn1": 1,
  "doorOut1": 1,
  "doorBlock1": 0,
  "doorIn2": 1,
  "doorOut2": 1,
  "doorBlock2": 0,
  "doorIn3": 0,
  "doorOut3": 0,
  "doorBlock3": 0,
  "date": "2025-08-22 11:26:12"
}
```

#### Response (JSON)

```json
{
    "data": {
        "door2In": 1,
        "door1In": 1,
        "checkinTime": "2025-08-22T11:26:12Z",
        "door3In": 0,
        "door2Block": 0,
        "latitude": 0.0,
        "door3Block": 0,
        "idVehicle": "EMBUSA50",
        "door1Out": 1,
        "door2Out": 1,
        "door3Out": 0,
        "door1Block": 0,
        "longitude": 0.0
    },
    "message": "Evento procesado exitosamente",
    "status": "OK"
}
```

Códigos de estado:
- `200 OK` → Procesado exitosamente
- `400 Bad Request` → Error en datos de entrada
- `404 Not Found` → Vehículo no encontrado
- `500 Internal Server Error` → Error inesperado

---

## Flujo de procesamiento

1. Puede consultar la documentación detallada del proceso en: [docs](./docs/PROCESS_README.md).

> ⚠️ Los eventos del **mismo vehículo** se procesan **secuencialmente** (1.7 evento/segundo aprox). Esto garantiza consistencia en los conteos.

---

## Instalación y despliegue

1. Clonar repositorio:
   ```bash
   git clone https://github.com/tu-org/passenger-events.git
   cd passenger-events
   ```

1. Configurar variables de entorno en `.env`:
   ```env
    DB_URL=
    DB_USER=
    DB_PASSWORD=
    HIBERNATE_DDL_AUTO=none
    HIBERNATE_FORMAT_SQL=true
    TIMEZONE=UTC
    KAFKA_BOOTSTRAP_SERVERS=
    KAFKA_CONSUMER_GROUP_ID=passenger-events-group
    KAFKA_AUTO_OFFSET_RESET=earliest
    KAFKA_CONSUMER_ENABLED=false
    SERVER_PORT=8080
    SERVER_CONTEXT_PATH=/api/v1
    PASSENGER_TOPIC=
    APP_EXCLUDED_IDS=COOCHOFAL250
    APP_PASSENGER_COUNT_TOLERANCE=200
    APP_TIME_THRESHOLD_MINUTES=45
    APP_TIMEZONE=America/Bogota
    APP_ASYNC_CORE_POOL_SIZE=2
    APP_ASYNC_MAX_POOL_SIZE=2
    APP_ASYNC_QUEUE_CAPACITY=500
   ```

2. Levantar con Docker Compose:
   ```bash
   docker compose up -d
   ```

3. Acceso API:
   ```
   http://localhost:8080/api/v1/passenger-events/sync/process-event
   ```

---

## Pruebas con JMeter

1. Abrir JMeter.
2. Crear un **Thread Group**.
3. Añadir un **HTTP Request**:
   - Method: `POST`
   - Path: `/api/v1/passenger-events/sync/process-event`
   - Body Data (JSON): evento como en [ejemplo](#request-json).
4. Añadir `HTTP Header Manager` con:
   ```
   Content-Type: application/json
   ```
5. Configurar número de threads y ramp-up para simular múltiples vehículos.

---

## Logs y debugging

Cada log incluye:
- `vehicleId` → Identificador de vehículo
- `messageId` → UUID por evento

Ejemplo:
```
2025-08-22 10:38:01 INFO  [vehicle=EMBUSA50 messageId=174141590889400] Evento insertado PassengerEvent(...)

```

Esto permite rastrear fácilmente qué logs corresponden a un mismo evento.

