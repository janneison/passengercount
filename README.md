# Passenger Count Service

Servicio backend para la **gestión y procesamiento de eventos de pasajeros en vehículos**.  
Permite recibir en tiempo real información de conteo (entradas/salidas por puerta, bloqueos, ubicación, timestamp) y mantener la consistencia de los datos por vehículo y viaje.

---

## Índice
1. [Arquitectura](#arquitectura)
2. [Tecnologías](#tecnologías)
3. [Endpoints](#endpoints)
4. [Flujo de procesamiento](#flujo-de-procesamiento)
5. [Instalación y despliegue](#instalación-y-despliegue)
6. [Variables de entorno](#variables-de-entorno)
7. [Pruebas con JMeter](#pruebas-con-jmeter)
8. [Logs y debugging](#logs-y-debugging)

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

2. Configurar variables de entorno en `.env`:

3. Levantar con Docker Compose:
   ```bash
   docker compose up -d
   ```

4. Acceso API:
   ```
   http://localhost:8080/api/v1/passenger-events/sync/process-event
   ```

---

## Variables de entorno

Estas variables controlan la configuración de la aplicación en tiempo de ejecución. 
Deben definirse en un archivo `.env` o inyectarse en el entorno donde se ejecute el servicio (ej. contenedor Docker, ECS, Kubernetes).

```env
# =======================
# BASE DE DATOS
# =======================
DB_URL=                     # URL de conexión JDBC a la base de datos (ej: jdbc:postgresql://host:5432/dbname)
DB_USER=                    # Usuario de conexión a la base de datos
DB_PASSWORD=                # Contraseña del usuario de base de datos
HIBERNATE_DDL_AUTO=none     # Estrategia de inicialización de Hibernate (none, validate, update, create, create-drop)
HIBERNATE_FORMAT_SQL=true   # Formatear las sentencias SQL en logs para mayor legibilidad

# =======================
# ZONA HORARIA
# =======================
TIMEZONE=UTC                # Zona horaria global de la app (ej: UTC, America/Bogota)

# =======================
# KAFKA
# =======================
KAFKA_BOOTSTRAP_SERVERS=     # Lista de brokers Kafka (ej: host1:9092,host2:9092)
KAFKA_CONSUMER_GROUP_ID=passenger-events-group   # ID del consumer group para consumir eventos
KAFKA_AUTO_OFFSET_RESET=earliest                 # Estrategia de lectura si no hay offset (earliest/latest/none)
KAFKA_CONSUMER_ENABLED=false                     # Habilitar o deshabilitar el consumo de Kafka (true/false)
PASSENGER_TOPIC=             # Nombre del tópico de Kafka de eventos de pasajeros

# =======================
# SERVIDOR
# =======================
SERVER_PORT=8080            # Puerto HTTP en el que expone el servicio
SERVER_CONTEXT_PATH=/api/v1 # Context path de la API REST

# =======================
# APLICACIÓN (APP)
# =======================
APP_EXCLUDED_IDS=COOCHOFAL250     # Lista de vehículos a excluir, separada por comas
APP_PASSENGER_COUNT_TOLERANCE=200 # Tolerancia en el conteo de pasajeros (ej: máximo permitido de diferencia)
APP_TIME_THRESHOLD_MINUTES=45     # Umbral de tiempo en minutos para validar eventos
APP_TIMEZONE=America/Bogota       # Zona horaria usada para procesar fechas de negocio

# =======================
# ASYNC (EJECUCIÓN CONCURRENTE)
# =======================
APP_ASYNC_CORE_POOL_SIZE=2   # Número mínimo de hilos en el pool
APP_ASYNC_MAX_POOL_SIZE=2    # Número máximo de hilos en el pool
APP_ASYNC_QUEUE_CAPACITY=500 # Capacidad máxima de la cola de tareas pendientes
```

---

## Ejemplo de archivo `.env` para entorno local

```env
   # Base de datos
   DB_URL=jdbc:postgresql://localhost:5432/passengers
   DB_USER=postgres
   DB_PASSWORD=postgres
   HIBERNATE_DDL_AUTO=update
   HIBERNATE_FORMAT_SQL=true

   # Zona horaria
   TIMEZONE=America/Bogota

   # Kafka
   KAFKA_BOOTSTRAP_SERVERS=localhost:9092
   KAFKA_CONSUMER_GROUP_ID=passenger-events-group
   KAFKA_AUTO_OFFSET_RESET=earliest
   KAFKA_CONSUMER_ENABLED=true
   PASSENGER_TOPIC=passenger-events

   # Servidor
   SERVER_PORT=8080
   SERVER_CONTEXT_PATH=/api/v1

   # Aplicación
   APP_EXCLUDED_IDS=COOCHOFAL250,EMBUSA99
   APP_PASSENGER_COUNT_TOLERANCE=200
   APP_TIME_THRESHOLD_MINUTES=45
   APP_TIMEZONE=America/Bogota

   # Async
   APP_ASYNC_CORE_POOL_SIZE=2
   APP_ASYNC_MAX_POOL_SIZE=2
   APP_ASYNC_QUEUE_CAPACITY=500
```

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

