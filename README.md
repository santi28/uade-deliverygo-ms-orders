# DeliveryGo · Módulo Pedidos (Grupo 5)

Microservicio de **Pedidos** del ecosistema DeliveryGo. Trabajo Práctico Integrador de Aplicaciones Interactivas (UADE, Facultad de Ingeniería y Ciencias Exactas).

Pedidos orquesta el ciclo de la compra: armar el carrito, revalidar precios, confirmar con el pago, aplicar el resultado del pago, pedir la entrega, cancelar y consultar. Es dueño de `Pedido`, `ItemPedido` e `HistorialEstado`, con base de datos propia. Usuarios, productos, pagos y entregas son de otros módulos: se referencian por ID y se validan por REST.

| | |
| --- | --- |
| Integrantes | Federico Abdo, Diego Derbenti, Santiago de Nicolás |
| Docente | Alejandro Foglino |
| Diseño y trazabilidad | [`doc/TPO_DeliveryGo_Pedidos_Entrega_Final.docx`](doc/TPO_DeliveryGo_Pedidos_Entrega_Final.docx) (planilla) y [`PLAN.md`](PLAN.md) (etapas, decisiones y desvíos) |

## Tecnologías

- Java 21, Spring Boot 3.5 (Web, Data JPA, Validation)
- PostgreSQL 16 en Docker (perfil `dev`); H2 en memoria para los tests
- springdoc-openapi 2.8 (Swagger UI)
- `RestClient` para las integraciones, con timeouts de 2 s de conexión y 3 s de lectura
- JUnit 5, Mockito, MockMvc y `MockRestServiceServer`
- Docker y Docker Compose; colección de Postman

## Cómo levantarlo

### Todo con Docker (app + base + datos de prueba)

```bash
cp .env.example .env        # cambiar la contraseña antes de levantar
docker compose up --build
```

- Swagger: http://localhost:8080/swagger-ui.html
- Healthcheck: http://localhost:8080/status (responde `OK`)

La app espera a que Postgres esté sano antes de arrancar. El perfil es `dev`, que carga los datos de prueba de `src/main/resources/db/seed-dev.sql`. La base queda en el volumen `pedidos_pgdata` y sobrevive a `docker compose down`; para empezar de cero, `docker compose down -v`.

### La app fuera de Docker

```bash
docker compose up -d postgres          # solo la base
./mvnw spring-boot:run                 # perfil dev por defecto
```

El `.env` lo lee Docker, no Spring. Si cambiaste las credenciales, exportá las mismas variables en el entorno donde corre la app.

Sin Docker, con la base en memoria (la que usa la colección de Postman y la que sirve para regenerar el Swagger):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

### Perfiles

| Perfil | Base | Integraciones | Uso |
| --- | --- | --- | --- |
| `dev` (por defecto) | Postgres del compose, con datos de prueba | Mocks | Desarrollo y demo |
| `test` | H2 en memoria | Mocks | Tests y pruebas rápidas sin Docker |
| `integracion` | La del perfil con el que se combine | Catálogo y Pagos reales | `-Dspring-boot.run.profiles=dev,integracion` |

## Variables de entorno

Todas tienen un valor por defecto, así que la app arranca sin configurar nada. Ver [`.env.example`](.env.example).

| Variable | Por defecto | Para qué |
| --- | --- | --- |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | `deliverygo` / `deliverygo` / `deliverygo_pedidos` | Credenciales de la base (compose y perfil `dev`) |
| `POSTGRES_HOST` / `POSTGRES_PORT` | `localhost` / `5432` | Dónde está la base cuando la app corre fuera de Docker |
| `SERVER_PORT` | `8080` | Puerto HTTP |
| `CATALOGO_BASE_URL` | `http://localhost:8082` | INT-01, con el perfil `integracion` |
| `PAGOS_BASE_URL` | `http://localhost:8084` | INT-03 a INT-05, con el perfil `integracion` |
| `PAGOS_MEDIO_PAGO_ID` | `cccccccc-…` | Medio de pago ya registrado en Pagos que usa INT-03 (ver limitaciones) |
| `USUARIOS_BASE_URL` | `http://localhost:8081` | Reservada para INT-02; hoy no la usa ningún cliente |

Propiedad de Spring, no variable: `deliverygo.pedidos.saltear-validacion-estado=true` apaga RN-04 (máquina de estados) en todos los CU. Está apagada por defecto y no se usa en la demo sin avisarle al docente (desvío del 17/11 en `PLAN.md`).

## Endpoints

Base: `/api/v1/orders`. La identidad del cliente viaja en la cabecera `X-Cliente-Id` (UUID). Todos los errores salen con el mismo formato: `{ timestamp, status, code, message, path, correlationId }`.

| CU | Método y ruta | Qué hace |
| --- | --- | --- |
| CU-01 | `POST /api/v1/orders` | Crea el pedido en BORRADOR (201) o devuelve el borrador abierto del cliente (200) |
| CU-02 | `POST /api/v1/orders/{id}/items` | Agrega líneas al borrador |
| CU-02 | `PUT /api/v1/orders/{id}/items/{productoId}` | Cambia la cantidad de una línea |
| CU-02 | `DELETE /api/v1/orders/{id}/items/{productoId}` | Quita una línea (idempotente) |
| CU-03 | `POST /api/v1/orders/{id}/confirm` | Revalida e inicia el pago; el pedido sigue en BORRADOR con el pago PENDIENTE |
| CU-04 | `DELETE /api/v1/orders/{id}` | Cancela; si estaba CONFIRMADO, pide el reembolso |
| CU-05 | `GET /api/v1/orders/{id}` | Pedido con ítems e historial de estados |
| CU-06 | `GET /api/v1/orders?pagina=0&tamanio=20` | Pedidos del cliente, paginados |
| CU-07 | `GET /api/v1/orders/{id}/price-check` | Compara el borrador con Catálogo, sin modificarlo |
| CU-09 | `POST /api/v1/orders/{id}/payment-result` | Lo llama Pagos (sin `X-Cliente-Id`): Pedidos consulta el pago y, si está aprobado, confirma |
| CU-10 | `POST /api/v1/orders/{id}/delivery` | Pide la entrega a Logística; el pedido pasa a EN_PREPARACION |
| — | `GET /status` | Healthcheck superficial: `OK` si la app sirve HTTP |

CU-08 (registrar transición e historial) no tiene endpoint: es interno y lo usan todos los CU que cambian el estado. Se ve en el `historial` de cada pedido.

Estados: `BORRADOR → CONFIRMADO → EN_PREPARACION`, y `BORRADOR` o `CONFIRMADO → CANCELADO`. Cualquier otra transición responde 409 `ORDER_INVALID_STATE`.

El contrato completo está en Swagger y exportado en [`doc/openapi.yaml`](doc/openapi.yaml).

## Integraciones

| ID | Módulo | Endpoint consumido | CU | Estado |
| --- | --- | --- | --- | --- |
| INT-01 | Catálogo | `GET /api/v1/productos/{id}` | CU-02, CU-03, CU-07 | HTTP real con `integracion`; mock sin él |
| INT-02 | Usuarios | `GET /api/v1/users/{id}` | CU-01, CU-03 | **Mock en todos los perfiles**: Usuarios no publica el recurso |
| INT-03 | Pagos | `POST /api/v1/payments` | CU-03 | HTTP real con `integracion`; mock sin él |
| INT-04 | Pagos | `POST /api/v1/payments/{codigoPago}/refund` | CU-04 | HTTP real con `integracion`; mock sin él |
| INT-05 | Pagos | `GET /api/v1/payments/{codigoPago}` | CU-09 | HTTP real con `integracion`; mock sin él |
| INT-06 | Logística | `POST /api/v1/deliveries/assign` | CU-10 | **Mock en todos los perfiles**: contrato incompatible (ver limitaciones) |

Todas propagan `Authorization` y `X-Correlation-Id`. Si un módulo no responde, el CU contesta 503 `DEPENDENCY_UNAVAILABLE` y no cambia nada: nunca se usa un precio ni un resultado supuesto.

Los mocks están en `client/mock` y se comportan como los módulos reales:

- Pagos crea los pagos en PENDIENTE y rechaza los de 100000 o más. Al consultar un pago que inició (CU-09), lo devuelve APROBADO. Reembolsa siempre.
- Logística asigna siempre y devuelve la misma entrega si se la pide dos veces para el mismo pedido.

## Datos de prueba

Los mocks y el seed comparten estos datos:

| Dato | Valor |
| --- | --- |
| Cliente activo | `11111111-1111-1111-1111-111111111111`, dirección `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa` |
| Cliente dado de baja (RN-01) | `22222222-2222-2222-2222-222222222222`, dirección `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb` |
| Comercios | `10` pizzería, `20` kiosco |
| Productos de la pizzería | `1` pizza (8500), `2` empanada (1200), `4` helado sin stock (RN-05) |
| Producto del kiosco | `3` gaseosa (sirve para RN-02) |

El perfil `dev` siembra dos pedidos del cliente activo:

- `dddd0001-0000-4000-8000-000000000001`, CONFIRMADO con pago: sirve para CU-05, para CU-04 con reembolso y para CU-10.
- `dddd0002-0000-4000-8000-000000000002`, CANCELADO: sirve para ver el 409 de RN-04.

A propósito no siembra ningún borrador, así el primer `POST /api/v1/orders` de la demo responde 201.

## Recorrido de la demo

1. `POST /api/v1/orders` con `{"comercioId": 10, "direccionEntregaId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}` → 201, BORRADOR.
2. `POST /{id}/items` con `{"items": [{"productoId": 1, "cantidad": 2}]}` → total 17000.
3. `GET /{id}/price-check` → `valido: true`.
4. `POST /{id}/confirm` con `{"medioPago": "TARJETA_CREDITO"}` → sigue en BORRADOR, con `pagoId`.
5. `POST /{id}/payment-result` con `{"codigoPago": "<pagoId>"}` → CONFIRMADO.
6. `POST /{id}/delivery` → EN_PREPARACION.
7. `GET /{id}` → el historial muestra cada paso.

Error controlado: agregar el producto `3` (otro comercio) → 422 `MIXED_MERCHANT`; o confirmar con 12 pizzas → 422 `PAYMENT_REJECTED`.

## Tests

```bash
./mvnw test
```

243 tests, en tres niveles: Service con Mockito (un archivo por CU), Controller con MockMvc (status y `ErrorResponse` de cada error) y, contra H2, rollback, persistencia, seed y el flujo completo de punta a punta. Los clientes HTTP se prueban contra el contrato publicado con `MockRestServiceServer`.

La colección [`postman/DeliveryGo-Pedidos.postman_collection.json`](postman/DeliveryGo-Pedidos.postman_collection.json) tiene 60 requests en orden de ejecución, con un caso por CU y por flujo alternativo. Corre contra la app con mocks (cualquier perfil menos `integracion`).

## Estructura

```
src/main/java/org/deliverygo/ordersms
  controller/   PedidoController, StatusController
  service/      PedidoService, MaquinaEstados
  repository/   PedidoRepository, HistorialEstadoRepository
  model/        Pedido, ItemPedido, HistorialEstado, EstadoPedido
  dto/          requests y responses de la API (DTO-01 a DTO-09)
  mapper/       PedidoMapper
  client/       interfaces de integración, implementaciones http/ y mock/, y sus DTO
  exception/    GlobalExceptionHandler, CodigoError y excepciones de negocio
  config/       OpenApiConfig, RestClientConfig
```

Cada clase y método relevante lleva su ID de trazabilidad (CU, RN, SRV, API, DTO, INT, REP, DM).

## Limitaciones conocidas

- **Pago asincrónico.** Pagos crea el pago en PENDIENTE y lo aprueba en una operación propia. Por eso CU-03 no confirma: confirma CU-09 cuando Pagos avisa. En la planilla de la primera entrega el pago era sincrónico.
- **Usuarios es mock** (INT-02). El módulo solo publica registro, login y recuperación de contraseña, y no expone direcciones. RN-01 y RN-06 se validan contra el mock, y la identidad llega en `X-Cliente-Id` sin JWT.
- **Logística es mock** (INT-06). Su `AsignarEntregaRequest` pide `pedidoId` y `clienteId` como `int64`, y los de Pedidos son UUID. Además pide direcciones en texto que ningún módulo expone. Hay que acordarlo con el equipo de Logística.
- **Medio de pago.** Pagos exige un `codigoMedioPago` (UUID) y `ConfirmarPedidoRequest` recibe el medio como texto. Hasta alinear DTO-04, se usa `PAGOS_MEDIO_PAGO_ID`.
- **Reintento después de un rechazo.** CU-03 manda siempre la clave de control `pedido-<id>`. Si Pagos la aplica, un reintento devuelve el mismo pago rechazado. Hay que acordarlo con Pagos.
- **Integraciones sin probar contra los servicios levantados.** Catálogo y Pagos están probados contra su contrato publicado (OpenAPI del ecosistema v1.4.0), no contra los servicios corriendo.
- **Pedido en preparación.** Un pedido EN_PREPARACION no se cancela: la entrega ya está en Logística y no hay contrato para darla de baja. Los estados EN_CAMINO y ENTREGADO no se alcanzan, porque Logística no le avisa a Pedidos.
- **Esquema sin migraciones.** El perfil `dev` genera el esquema con `ddl-auto=update`.
- **`docker compose up --build` sin verificar en una máquina limpia.** Está verificado que el jar se empaqueta con el nombre que copia la imagen y que el seed carga.
