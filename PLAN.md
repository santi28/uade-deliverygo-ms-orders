# DeliveryGo · Módulo Pedidos (Grupo 5) — Plan de desarrollo por etapas

> Contexto del repo para el equipo. Fuente de verdad del diseño: planilla `TPO_DeliveryGo_Primera_Entrega` (aprobada con correcciones). Si el código y la planilla no coinciden, se corrige uno de los dos y se deja registrado.

## Contexto del módulo

Pedidos orquesta el ciclo funcional de la compra. Es dueño de `Pedido`, `ItemPedido` e `HistorialEstado`. **No** es dueño de usuarios, productos, pagos ni entregas: esos se referencian por ID y se validan por REST.

**CU CORE (alcance Etapa 2 de la materia):**

| CU | Nombre | Endpoint | Servicio |
| --- | --- | --- | --- |
| CU-01 | Crear pedido o borrador | `POST /api/v1/orders` | `crearPedido()` |
| CU-02 | Agregar, modificar o eliminar ítems | `POST /api/v1/orders/{id}/items` | `gestionarItems()` |
| CU-03 | Confirmar pedido | `POST /api/v1/orders/{id}/confirm` | `confirmarPedido()` |
| CU-04 | Cancelar pedido | `POST /api/v1/orders/{id}/cancel` | `cancelarPedido()` |
| CU-05 | Consultar pedido y estado | `GET /api/v1/orders/{id}` | `consultarPedido()` |

Evolución (solo si sobra tiempo): CU-06 listar pedidos del usuario, CU-10 solicitar asignación a Logística.

**Reglas de negocio**

| ID | Regla | Dónde se valida |
| --- | --- | --- |
| RN-01 | Todo pedido pertenece a un usuario registrado y autenticado | Service + INT-02 |
| RN-02 | Todos los productos son del mismo comercio | Service (`comercioId` del pedido) |
| RN-03 | El total se calcula con precios confirmados | `ItemPedido.precioUnitario` congelado + INT-01 |
| RN-04 | Los estados siguen transiciones válidas | Máquina de estados en Service + `HistorialEstado` |
| RN-05 | Solo productos activos y disponibles en Catálogo | INT-01 |
| RN-06 | Todo pedido tiene dirección de entrega válida | INT-02 |

**Reglas de arquitectura (de la consigna, no negociables)**

- Base de datos propia; nunca leer tablas de otro módulo.
- Módulos ajenos referenciados por IDs planos (`clienteId`, `comercioId`, `productoId`, `pagoId`), sin `@ManyToOne` hacia ellos (DM-03).
- DTOs externos nunca se usan como entidades JPA.
- Errores siempre con el formato normalizado (`ErrorResponse`).
- Swagger accesible durante la demo.
- Mínimo 3 integraciones reales. Todo mock que quede se declara como mock.

## Decisiones técnicas

| Tema | Decisión |
| --- | --- |
| Stack | Java 21, Spring Boot 3, Spring Data JPA / Hibernate |
| BD | PostgreSQL en Docker (perfil `dev`), H2 para tests |
| IDs | UUID |
| Estado | `enum EstadoPedido` con `@Enumerated(STRING)` (DM-04) |
| Relaciones | `FetchType.LAZY` + `JOIN FETCH` puntual (DM-02); ítems e historial por cascade desde Pedido (DM-06) |
| Montos | `BigDecimal`, moneda `ARS` |
| Docs API | springdoc-openapi (`/swagger-ui.html`) |
| Validación | Bean Validation en DTOs |
| Clientes HTTP | `RestClient` detrás de interfaces; implementación real con el perfil `integracion` y mock (`@Profile("!integracion")`) cuando no está activo |

**Estructura de paquetes**

```
com.deliverygo.pedidos
  controller/   PedidoController
  service/      PedidoService, MaquinaEstados
  repository/   PedidoRepository, HistorialEstadoRepository
  domain/       Pedido, ItemPedido, HistorialEstado, EstadoPedido
  dto/          CrearPedidoRequest, ModificarItemsPedidoRequest, ConfirmarPedidoRequest, PedidoResponse, ErrorResponse
  mapper/       PedidoMapper
  client/       CatalogoClient, UsuariosClient, PagosClient (+ impl mock y http) y client/dto
  exception/    GlobalExceptionHandler, PedidoNotFoundException, EstadoInvalidoException, ReglaNegocioException
  config/       OpenApiConfig, RestClientConfig
```

**Máquina de estados (RN-04)**

| Desde | Hacia | Disparador |
| --- | --- | --- |
| BORRADOR | BORRADOR | CU-02 modificar ítems |
| BORRADOR | CONFIRMADO | CU-03 con pago APROBADO |
| BORRADOR | BORRADOR | CU-03 con pago RECHAZADO (DM-07) |
| BORRADOR | CANCELADO | CU-04, sin reembolso |
| CONFIRMADO | CANCELADO | CU-04, solo si el reembolso responde REEMBOLSADO |
| CONFIRMADO | EN_PREPARACION / EN_CAMINO / ENTREGADO | Evolución (Logística), fuera de CORE |

Cualquier otra transición → `ORDER_INVALID_STATE` y no se persiste nada.

**Repositorios (versión canónica, hoja 11)**

| ID | Consulta | CU |
| --- | --- | --- |
| REP-01 | `PedidoRepository.findByClienteIdAndEstado` — borrador activo del cliente | CU-01 |
| REP-02 | `PedidoRepository.findByIdConItems` (JOIN FETCH) — pedido + ítems | CU-02, CU-03 |
| REP-03 | `PedidoRepository.findByIdAndClienteId` — pedido verificando dueño | CU-04, CU-05 |
| REP-04 | `HistorialEstadoRepository.findByPedidoIdOrderByFechaCambioAsc` | CU-05 |

No existen REP-05 ni `ItemPedidoRepository`.

**Integraciones**

| ID | Módulo | Endpoint consumido | CU | Si falla |
| --- | --- | --- | --- | --- |
| INT-01 | Catálogo (Eq. 2) | `GET /api/v1/products/{id}` | CU-01, 02, 03 | 503; nunca usar un precio supuesto |
| INT-02 | Usuarios (Eq. 1) | `GET /api/v1/users/{id}` (+ direcciones) | CU-01, 03 | 503; no crear ni confirmar |
| INT-03 | Pagos (Eq. 4) | `POST /api/v1/payments` | CU-03 | Pedido sigue en BORRADOR |
| INT-04 | Pagos (Eq. 4) | `POST /api/v1/payments/{pagoId}/refund` | CU-04 | Pedido no cambia de estado |

Todas: timeout 2–3 s, propagar `Authorization` y `X-Correlation-Id`.

**Contrato con Pagos (propuesta, a confirmar con Eq. 4)**

- CU-03: `POST /api/v1/payments`, header `Idempotency-Key: <pedidoId>`, body `{orderId, userId, amount, currency, paymentMethod}` → 201 `{paymentId, status, amount}` con `status` APROBADO o RECHAZADO resuelto en la misma llamada.
- Si APROBADO: se guarda `pedido.pagoId` y se pasa a CONFIRMADO en la misma transacción. Si RECHAZADO: queda en BORRADOR y se responde 422 `PAYMENT_REJECTED`.
- CU-04: si el pedido está CONFIRMADO, `POST /api/v1/payments/{pagoId}/refund` con `Idempotency-Key: refund-<pedidoId>`. Solo con REEMBOLSADO pasa a CANCELADO.
- Pagos valida el importe consumiendo nuestro `GET /api/v1/orders/{id}`.
- Si Pagos devuelve PENDIENTE y aprueba aparte, CU-03 cambia y CU-09 pasa a ser necesario → decidir con Eq. 4 y el docente.

**Errores**

| Código | HTTP | Cuándo |
| --- | --- | --- |
| VALIDATION_ERROR | 400 | Dato faltante o cantidad inválida (RN-01) |
| ORDER_NOT_FOUND | 404 | Pedido inexistente o de otro cliente |
| ORDER_INVALID_STATE | 409 * | Transición no permitida (RN-04) |
| MIXED_MERCHANT | 422 | Producto de otro comercio (RN-02) |
| PRODUCT_UNAVAILABLE | 422 | Producto inactivo o sin stock (RN-05) |
| PRICE_CHANGED | 422 | Precio distinto al confirmar (RN-03) |
| PAYMENT_REJECTED | 422 | Pagos rechazó |
| DEPENDENCY_UNAVAILABLE | 503 | Catálogo, Usuarios o Pagos no responde |

\* La consigna usa 409 para conflicto de estado, pero su ejemplo muestra `ORDER_INVALID_STATE` con 422. Pendiente de confirmar con el docente.

Formato: `{ timestamp, status, code, message, path, correlationId }`.

---

## Etapas

Fechas atadas al cronograma de la materia. Cada etapa tiene criterio de salida: no se pasa a la siguiente sin cumplirlo.

### Etapa 0 — Correcciones de diseño · hasta 16/10

**Objetivo:** cerrar lo que pidió el docente en la devolución antes de escribir Services.

Normalizar REP:
- [ ] SEC-02: reemplazar REP-04 e `ItemPedidoRepository` por REP-02 (el ítem se busca dentro del pedido)
- [ ] SEC-02: renombrar `gestionarPedido()` → `gestionarItems()` y rehacer el diagrama
- [ ] SEC-03: REP-03 → REP-02
- [ ] SEC-04: REP-01 → REP-03
- [ ] SEC-05: REP-05 → REP-03 + REP-04
- [ ] Hoja 9: sacar REP-05
- [ ] Hoja 11: corregir descripción de REP-03

Contrato con Pagos:
- [ ] Reunión con Eq. 4: autorización, `paymentId`, asociación y reembolso
- [ ] INT-03 pasa a `POST /payments`; agregar INT-04 `POST /payments/{id}/refund` (matriz, hoja 12, SEC-03, SEC-04)
- [ ] ENT-01: agregar `pagoId`; DTO-02: `medioPagoId` → `pagoId`
- [ ] DM-09: Pedido guarda solo `pagoId`, no el estado del pago

Pendientes de la planilla:
- [ ] Completar DM-06, DM-07, DM-08 (alternativas y motivo)
- [ ] Alinear RN de las fichas CU-02 y CU-04 con la matriz
- [ ] Corregir "HisotrialEstado" en CU-03 y CU-05
- [ ] Agregar `historial` a DTO-02 (lo usa CU-05)
- [ ] Definir DTO-03 con cantidad 0 (quita ítem) y resultado negativo (400)
- [ ] Revisar SEC-03 a SEC-05; completar estado del checklist (hoja 14)
- [ ] Hoja 10: sacar "EJEMPLO:"; portada con integrantes y fecha
- [ ] Confirmar con el docente 409 vs 422 para estado inválido

**Salida:** planilla sin referencias contradictorias y contrato con Pagos acordado por escrito.

### Etapa 1 — Esqueleto y modelo · hasta 20/10

**Objetivo:** proyecto que levanta, persiste y responde los primeros endpoints con mocks.

- [x] Proyecto Spring Boot + `docker-compose.yml` con PostgreSQL + perfiles `dev` (Postgres) y `test` (H2)
- [x] Entidades `Pedido`, `ItemPedido`, `HistorialEstado`, enum `EstadoPedido` (REL-01 composición con cascade + orphanRemoval, REL-02)
- [x] `PedidoRepository` (REP-01..03) y `HistorialEstadoRepository` (REP-04) + tests `@DataJpaTest`
- [x] DTOs, `PedidoMapper`, `GlobalExceptionHandler` con `ErrorResponse`
- [x] Interfaces `CatalogoClient`, `UsuariosClient`, `PagosClient` + implementaciones mock
- [x] SRV-01 `crearPedido()` y SRV-05 `consultarPedido()`
- [x] `.env.example` y plantilla de PR

**Salida:** CU-01 y CU-05 funcionan desde Swagger con mocks. **Etapa cerrada.**

Desvíos registrados:

- No existe el perfil `mock`: los mocks son la implementación por defecto y se apagan activando `integracion` (Etapa 4). Un perfil `mock` aparte obligaría a activarlo en cada corrida y rompería el contexto de los tests. Los mocks siguen declarados como mock.
- `dev` genera el esquema con `ddl-auto=update`. Si el modelo se estabiliza, pasar a migraciones versionadas.
- El README se arma en la Etapa 5, con el proyecto terminado, en vez de escribir una versión inicial que haya que reescribir entera.

### Etapa 2 — CU CORE completos con mocks · hasta 27/10

**Objetivo:** los 5 endpoints obligatorios funcionando con todas sus reglas.

- [ ] `MaquinaEstados` con registro en `HistorialEstado` + tests unitarios
- [ ] SRV-02 `gestionarItems()`: alta, cambio, baja, recálculo de total, RN-02, RN-05
- [ ] SRV-03 `confirmarPedido()` `@Transactional`: revalidar usuario, dirección, precios y disponibilidad; pago; transición + historial
- [ ] SRV-04 `cancelarPedido()`: estado cancelable, reembolso si CONFIRMADO
- [ ] Mocks ajustados a los DTOs reales acordados con Catálogo, Usuarios y Pagos
- [ ] Swagger con ejemplos y respuestas de error por endpoint
- [ ] Colección Postman con un caso por CU y por alternativa

**Salida:** los 5 CU CORE responden bien y todos los errores salen normalizados.

### Etapa 3 — Calidad · hasta 03/11

**Objetivo:** que se pueda probar y levantar desde cero.

- [ ] Tests de Service con Mockito: camino feliz + cada flujo alternativo de CU-01..05
- [ ] Tests de Controller con MockMvc: 400, 404, 409/422, 503
- [ ] Tests de rollback en confirmar y cancelar
- [ ] Timeouts y manejo de dependencia caída (503, sin datos inventados)
- [ ] Dockerfile + compose que levanta app + BD + datos de prueba
- [ ] Diagramas actualizados si el código cambió algo

**Salida:** `docker compose up` en una máquina limpia, seed cargado y tests en verde.

### Etapa 4 — Integración real · hasta 10/11

**Objetivo:** reemplazar mocks por los servicios de los otros equipos.

- [ ] INT-01 Catálogo real
- [ ] INT-02 Usuarios real (JWT incluido)
- [ ] INT-03 / INT-04 Pagos real
- [ ] Registro de integraciones: URL, versión, responsable, evidencia de prueba exitosa
- [ ] Declarar explícitamente lo que siga mockeado
- [ ] (Opcional) CU-06 listar pedidos paginado y CU-10 solicitud a Logística
- [ ] Revisar N+1 e índices en `clienteId` y `estado`

**Salida:** flujo crear → agregar ítems → confirmar con pago real → cancelar con reembolso real.

### Etapa 5 — Defensa técnica · hasta 17/11

**Objetivo:** demo estable y todos pueden explicar cualquier parte.

- [ ] Matriz de trazabilidad revisada contra el código mergeado
- [ ] README, de cero y completo (descripción, integrantes, tecnologías, ejecución con `docker compose` y perfiles, variables de entorno, endpoints, integraciones, datos de prueba, limitaciones)
- [ ] Acta de decisiones técnicas actualizada
- [ ] Guion de demo: caso principal, 3 integraciones, un error controlado
- [ ] Ronda cruzada: cada integrante explica un CU que no programó
- [ ] Ensayo completo de 30 min (15 o 16/11)

**Salida:** ensayo hecho sin cortes y sin depender de una sola persona.

---

## Forma de trabajo

- Ramas: `main` (entregas), `develop` (integración), `feature/cu-03-confirmar`, `fix/<tema>`.
- PR obligatorio a `develop` con al menos 1 review de otro integrante. Nada directo a `main`.
- Commits con ID de trazabilidad: `feat(CU-03): confirmar pedido con pago (SRV-03, API-03)`.
- Sin secretos en el repo: `.env` local, `.env.example` commiteado.
- Una issue por ítem de este plan, con la etiqueta de su etapa (`etapa-0` … `etapa-5`).

## Definition of Done (por CU)

- [ ] Endpoint coincide con API/DTO de la planilla y se prueba desde Swagger
- [ ] Reglas de negocio en el Service, no en el Controller
- [ ] Validación, inexistente y estado inválido devuelven `ErrorResponse`
- [ ] Tests del camino feliz y de cada alternativa en verde
- [ ] Ninguna consulta a datos de otro módulo; solo IDs y snapshots
- [ ] PR revisado y mergeado; trazabilidad actualizada si algo cambió
