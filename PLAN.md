# DeliveryGo · Módulo Pedidos (Grupo 5) — Plan de desarrollo por etapas

> Contexto del repo para el equipo. Fuente de verdad del diseño: planilla `TPO_DeliveryGo_Primera_Entrega` (aprobada con correcciones). Si el código y la planilla no coinciden, se corrige uno de los dos y se deja registrado.

## Contexto del módulo

Pedidos orquesta el ciclo funcional de la compra. Es dueño de `Pedido`, `ItemPedido` e `HistorialEstado`. **No** es dueño de usuarios, productos, pagos ni entregas: esos se referencian por ID y se validan por REST.

**CU CORE (alcance Etapa 2 de la materia):**

| CU | Nombre | Endpoint | Servicio |
| --- | --- | --- | --- |
| CU-01 | Crear pedido o borrador | `POST /api/v1/orders` | `crearPedido()` |
| CU-02 | Agregar ítems | `POST /api/v1/orders/{id}/items` | `agregarItems()` |
| CU-02 | Modificar la cantidad de un ítem | `PUT /api/v1/orders/{id}/items/{productoId}` | `modificarCantidadItem()` |
| CU-02 | Eliminar un ítem | `DELETE /api/v1/orders/{id}/items/{productoId}` | `quitarItem()` |
| CU-03 | Confirmar pedido | `POST /api/v1/orders/{id}/confirm` | `confirmarPedido()` |
| CU-04 | Cancelar pedido | `DELETE /api/v1/orders/{id}/cancel` | `cancelarPedido()` |
| CU-05 | Consultar pedido y estado | `GET /api/v1/orders/{id}` | `consultarPedido()` |

Evolución (solo si sobra tiempo): CU-06 listar pedidos del usuario, CU-10 solicitar asignación a Logística.

**Desvío registrado — CU-02 abierto en tres endpoints (27/10)**

La planilla define CU-02 como un único `POST /api/v1/orders/{id}/items` que recibía la cantidad final de cada línea y usaba `cantidad = 0` para quitarla. Por pedido del equipo se partió en un verbo por operación: `POST` da de alta, `PUT` cambia la cantidad de una línea y `DELETE` la saca. El CU, la regla de negocio y el `HistorialEstado` son los mismos; cambian la API (API-02, API-02b, API-02c) y los DTOs.

Qué hay que corregir en la planilla: la ficha de CU-02, API-02, SEC-02 y el diagrama de secuencia, y DTO-03 (que ya no lleva cantidad 0 y ahora exige cantidad mayor a cero). `ModificarItemsPedidoRequest` quedó reemplazado por `AgregarItemsPedidoRequest` (DTO-03) y `ModificarCantidadItemRequest` (DTO-05).

Decisiones que trajo el cambio, a confirmar con el docente:

- Un `POST` de un producto que ya está en el pedido responde 400 en vez de acumular cantidad: el POST repetido no debería sumar sin que el cliente lo pida.
- Un `PUT` sobre un producto que no está en el pedido responde 400 `VALIDATION_ERROR`, no 404. La tabla de errores es cerrada y `ORDER_NOT_FOUND` está definido para el pedido, no para la línea. Si se quiere el 404 de REST, hay que agregar un código `ITEM_NOT_FOUND` a la tabla.
- El `DELETE` es idempotente: una línea que no está responde 200 con el pedido sin cambios y no deja entrada en el historial.
- Los tres responden 200 con el pedido completo y recalculado, no 201 ni 204: después de cada cambio lo que hace falta mostrar es el total nuevo.

**Reglas de negocio**

| ID | Regla | Dónde se valida |
| --- | --- | --- |
| RN-01 | Todo pedido pertenece a un usuario registrado y autenticado | Service + INT-02 |
| RN-02 | Todos los productos son del mismo comercio | Service (`comercioId` del pedido) |
| RN-03 | El total se calcula con precios confirmados | `ItemPedido.precioUnitario` congelado + INT-01 |
| RN-04 | Los estados siguen transiciones válidas | `MaquinaEstados` en Service + `HistorialEstado` |
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
  dto/          CrearPedidoRequest, AgregarItemsPedidoRequest, ModificarCantidadItemRequest,
                ConfirmarPedidoRequest, PedidoResponse, ErrorResponse
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

- [x] `MaquinaEstados` con registro en `HistorialEstado` + tests unitarios
- [x] SRV-02 alta, cambio y baja de ítems, recálculo de total, RN-02, RN-05 (`agregarItems()`, `modificarCantidadItem()`, `quitarItem()`)
- [x] SRV-03 `confirmarPedido()` `@Transactional`: revalidar usuario, dirección, precios y disponibilidad; pago; transición + historial
- [x] SRV-04 `cancelarPedido()`: estado cancelable, reembolso si CONFIRMADO
- [ ] Mocks ajustados a los DTOs reales acordados con Catálogo, Usuarios y Pagos
- [x] Swagger con ejemplos y respuestas de error por endpoint
- [x] Colección Postman con un caso por CU y por alternativa (`postman/`, 40 requests)

**Salida:** los 5 CU CORE responden bien y todos los errores salen normalizados. **Cumplida**, con un ítem abierto.

Pendiente, no depende de nosotros: ajustar los mocks a los DTOs reales necesita que Catálogo, Usuarios y Pagos cierren sus contratos. Hasta entonces los mocks siguen con los DTOs propuestos en esta planilla, declarados como mock. Se cierra junto con la Etapa 4.

Sin ejercitar de punta a punta: `PRICE_CHANGED` y `PRODUCT_UNAVAILABLE` en CU-03. El mock de Catálogo tiene precios fijos y CU-02 no deja agregar un producto no disponible, así que no hay forma de llegar a esos errores desde la API. Se cubren con los tests de Mockito de la Etapa 3.

### Etapa 3 — Calidad · hasta 03/11

**Objetivo:** que se pueda probar y levantar desde cero.

- [x] Tests de Service con Mockito: camino feliz + cada flujo alternativo de CU-01..05
- [x] Tests de Controller con MockMvc: 400, 404, 409/422, 503
- [x] Tests de rollback en confirmar y cancelar
- [x] Timeouts y manejo de dependencia caída (503, sin datos inventados)
- [x] Dockerfile + compose que levanta app + BD + datos de prueba
- [ ] Diagramas actualizados si el código cambió algo

**Salida:** `docker compose up` en una máquina limpia, seed cargado y tests en verde.

Tests de Service: una clase por CU (`CrearPedidoServiceTest`, `GestionarItemsServiceTest`, `ConfirmarPedidoServiceTest`, `CancelarPedidoServiceTest`, `ConsultarPedidoServiceTest`) sobre `PedidoServiceTestBase`, con repositorios y clientes de integración mockeados y `MaquinaEstados` y `PedidoMapper` reales. 60 tests; con eso quedan cubiertos `PRICE_CHANGED` y `PRODUCT_UNAVAILABLE` de CU-03, que la Etapa 2 no podía ejercitar desde la API.

Tests de Controller: `PedidoControllerTest` con `@WebMvcTest` y el Service mockeado, porque lo que se verifica es la capa web: el status y el `ErrorResponse` de cada código de la tabla (400, 404, 409, 422, 503, más el 500 de contención y el 405 de transporte), la validación de los DTOs, el `Location` de API-01 y que cada endpoint le pase al Service la identidad de `X-Cliente-Id` y los ids de la ruta. El camino completo contra la base sigue en `PedidoControllerRegressionTest`.

Tests de rollback: `PedidoServiceRollbackTest` con `@SpringBootTest` contra H2, sin `@Transactional` en el test (envolverlo en una transacción propia taparía justamente el rollback que se quiere observar) y con solo las integraciones mockeadas. Cada caso corre el CU y después vuelve a leer la base en transacciones nuevas. Incluye la verificación de DM-07: con pago RECHAZADO se deshace la confirmación pero la entrada del intento sobrevive, porque va en una transacción `REQUIRES_NEW`. Las lecturas que miran las líneas usan REP-02: `getItems()` es LAZY y fuera de la transacción ya no hay sesión (DM-02).

Timeouts: `RestClientConfig` reemplaza al `RestClient.Builder` que autoconfigura Spring Boot, que viene **sin timeouts** (una llamada saliente esperaba indefinidamente). Centraliza lo que el plan pide para INT-01..04: `connect-timeout` 2s y `read-timeout` 3s, ajustables con `deliverygo.integraciones.*`, y la propagación de `Authorization` y `X-Correlation-Id` de la petición en curso. Están ahí y no en cada cliente para que ninguna integración quede sin timeout. `RestClientConfigTest` lo verifica contra un servidor HTTP del JDK que no contesta: un mock no sirve, porque no deja de responder.

Lo que falta de este ítem depende de la Etapa 4: traducir el fallo de transporte a `DependenciaNoDisponibleException` es tarea de cada cliente HTTP, porque el mensaje nombra al módulo que no respondió, y esos clientes todavía no existen. El resto del camino del 503 ya está probado en los tres niveles: el Service propaga, el Controller responde 503 con `ErrorResponse` y el rollback no deja rastro.

Docker: `Dockerfile` en dos etapas (se compila con el JDK, se corre con el JRE y con usuario sin privilegios) y el `docker-compose.yml` ahora levanta también el servicio `app`, que espera el healthcheck de Postgres antes de arrancar. Los tests no corren en el build de la imagen: se empaqueta con `-DskipTests` lo que ya está en verde.

Datos de prueba en `src/main/resources/db/seed-dev.sql`, cargados solo por el perfil `dev` (`spring.sql.init.data-locations` + `defer-datasource-initialization`, para que corran después de que Hibernate crea el esquema). Siembra un pedido CONFIRMADO con pago —sirve para CU-05 y para CU-04 con reembolso— y uno CANCELADO, para mostrar el 409 de RN-04. **No** siembra ningún BORRADOR a propósito: CU-01 reutiliza el borrador abierto del cliente (REP-01), así que un borrador sembrado haría que el primer POST de la demo respondiera 200 en lugar de 201. El script es idempotente porque corre en cada arranque y la base del compose es un volumen; `DatosDePruebaDevTest` valida contra H2 los nombres de columna, los totales, el orden del historial y la idempotencia.

Falta verificar `docker compose up --build` en una máquina con Docker: no hay Docker en el entorno donde se escribieron el Dockerfile y el compose. Lo que sí está verificado es que `./mvnw clean package -DskipTests` produce el jar con el nombre que copia la imagen, y que el seed carga. Hasta que alguien corra el `up` en limpio, la salida de la etapa no está cumplida.

Sin clases `@Nested`: surefire las descarta en una corrida normal (el nombre `...Test$Caso` no coincide con sus patrones de inclusión) y los tests dejan de ejecutarse sin que nada avise. Los casos de los tres verbos de CU-02 van en una sola clase, con el prefijo de la API en el nombre.

### Etapa 4 — Integración real · hasta 10/11

**Objetivo:** reemplazar mocks por los servicios de los otros equipos.

- [x] INT-01 Catálogo real — `CatalogoClientHttp`, escrito contra el OpenAPI del ecosistema; falta probarlo contra el servicio levantado
- [ ] ~~INT-02 Usuarios real (JWT incluido)~~ — **no se puede**: el módulo no expone el recurso. Ver desvío abajo
- [x] INT-03 / INT-04 Pagos real — `PagosClientHttp`; falta probarlo contra el servicio levantado
- [ ] Registro de integraciones: URL, versión, responsable, evidencia de prueba exitosa
- [ ] Declarar explícitamente lo que siga mockeado
- [ ] (Opcional) CU-06 listar pedidos paginado y CU-10 solicitud a Logística
- [ ] Revisar N+1 e índices en `clienteId` y `estado`
- [ ] CU-09: endpoint para el aviso de Pagos, del que ahora depende pasar a CONFIRMADO

**Salida:** flujo crear → agregar ítems → confirmar con pago real → cancelar con reembolso real.

**Desvíos registrados — contratos reales del ecosistema (03/11)**

Llegó el OpenAPI consolidado del ecosistema (`swagger-ecosistema.txt`, v1.4.0) con las APIs de los cinco módulos. Los contratos que esta planilla proponía para INT-01..04 eran suposiciones nuestras y varios no coinciden. Lo que se cambió en el código:

| # | Qué asumíamos | Qué publica el ecosistema | Qué se hizo |
| --- | --- | --- | --- |
| 1 | `GET /api/v1/products/{id}` | `GET /api/v1/productos/{id}` | Ruta corregida en INT-01 |
| 2 | `productoId` y `comercioId` UUID | `integer int64` | **Migrados a `Long`** en `Pedido`, `ItemPedido`, DTOs, controller, seed, tests y Postman. Catálogo es dueño de esos IDs, así que el tipo lo define quien los emite (DM-03) |
| 3 | 404 = producto inexistente | 404 = inexistente **o inactivo** | `Optional.empty()` significa "no se puede pedir" |
| 4 | Un campo `disponible` | `disponible` + `estado` | RN-05 pasa a ser la conjunción de los dos: `ProductoDto.vendible()` |
| 5 | `POST /payments` resuelve el cobro | 201 **PENDIENTE** siempre; autoriza en `POST /{codigoPago}/approve`, que es un CU de ellos | Ver abajo |
| 6 | `Idempotency-Key` en cabecera | `claveControl` en el cuerpo | `pedido-<pedidoId>` y `reembolso-<pedidoId>` |
| 7 | `{orderId, userId, amount, currency, paymentMethod}` | `{codigoPedido, codigoUsuario, codigoMedioPago, monto, moneda, claveControl}` | DTOs de integración rehechos |
| 8 | El reembolso devuelve el pago en REEMBOLSADO | devuelve un `ReembolsoResponse` con su propio `estado` (APROBADO) | CU-04 mira el estado **del reembolso**, no del pago |

**Desvío de CU-03 — el pago es asincrónico.** Era el riesgo que esta planilla ya anotaba ("si Pagos devuelve PENDIENTE y aprueba aparte, CU-03 cambia y CU-09 pasa a ser necesario"). Pasó. Decisión del equipo (03/11): `confirmarPedido()` inicia el pago, guarda el `pagoId`, deja el pedido **en BORRADOR** y asienta el intento en el historial (`BORRADOR → BORRADOR`, igual que DM-07). Responde 200 con el pedido, no 422: no hubo error, el pago quedó en curso. Pasar a CONFIRMADO ahora depende de CU-09.

Qué hay que corregir en la planilla: la ficha de CU-03, API-03, SEC-03, el diagrama de secuencia, INT-03, la máquina de estados (BORRADOR → CONFIRMADO deja de ser disparada por CU-03) y DM-07. **Hay que avisarle al docente**: el CU-03 que se entregó en la Etapa 2 ya no cierra el flujo por sí solo, y CU-09 pasa de evolución a obligatorio.

**Desvío de INT-02 — Usuarios no expone el recurso.** El módulo publica solo `/auth/register`, `/auth/login` y `/auth/password/forgot`; no hay `GET /api/v1/users/{id}` y `UsuarioResponse` no trae direcciones. En todo el ecosistema no hay recurso de direcciones del usuario. Consecuencia: **RN-01 y RN-06 no se validan contra ningún módulo**. Decisión del equipo (03/11): no se usa JWT y se confía en que el usuario viene validado por su propio módulo. `UsuariosClientMock` queda activo en todos los perfiles (no se apaga con `integracion`) porque no hay implementación HTTP que lo reemplace, y está declarado como mock. Las tres integraciones reales que pide la consigna se cumplen con INT-01, INT-03 e INT-04.

**Desvío de DTO-04 — falta el medio de pago.** Pagos exige un `codigoMedioPago` (UUID) que se registra antes con `POST /api/v1/payment-methods`, y `ConfirmarPedidoRequest.medioPago` es un texto (`"TARJETA_CREDITO"`). Hasta alinear DTO-04, el código sale de `deliverygo.integraciones.pagos.medio-pago-por-defecto` y el texto del request no se usa. Es lo más flojo de esta tanda: conviene que DTO-04 pase a recibir un `medioPagoId`.

**Qué queda sin verificar.** Los dos clientes están probados con `MockRestServiceServer` contra el contrato publicado (16 tests), no contra los servicios corriendo. Hasta que alguien levante Catálogo y Pagos y se corra el flujo, los ítems de arriba no están cerrados y el registro de integraciones no tiene evidencia.

### Etapa 5 — Defensa técnica · hasta 17/11

**Objetivo:** demo estable y todos pueden explicar cualquier parte.

- [ ] Matriz de trazabilidad revisada contra el código mergeado
- [ ] README, de cero y completo (descripción, integrantes, tecnologías, ejecución con `docker compose` y perfiles, variables de entorno, endpoints, integraciones, datos de prueba, limitaciones)
- [ ] Acta de decisiones técnicas actualizada
- [ ] Guion de demo: caso principal, 3 integraciones, un error controlado
- [ ] Ronda cruzada: cada integrante explica un CU que no programó
- [ ] Ensayo completo de 30 min (15 o 16/11)

**Salida:** ensayo hecho sin cortes y sin depender de una sola persona.

**Desvío registrado — endpoint `/status` fuera de la planilla (17/11)**

Se agregó `GET /status`, que responde 200 con el cuerpo literal `OK` en `text/plain`. **No está en la planilla**: no es un CU ni un API-xx, así que no tiene ID de trazabilidad y queda declarado como agregado del equipo. Lo pide la operación, no el negocio: el healthcheck del contenedor `app` en `docker-compose.yml`, la demo y los otros módulos necesitan una forma de comprobar que el servicio responde.

Decisiones:

- Vive en `StatusController`, fuera de `/api/v1/orders`, así que no pide `X-Cliente-Id`.
- Es un health **superficial**: no toca la base ni las integraciones. Un 200 significa solo que el proceso está levantado y sirviendo HTTP. Si mirara Postgres o Catálogo, una dependencia caída marcaría el contenedor como `unhealthy` y lo reiniciaría en vez de responder 503 por CU, que es lo que define el plan (DEPENDENCY_UNAVAILABLE).
- Devuelve texto plano, no el `ErrorResponse` normalizado ni un JSON de estado: el contrato es el status 200 y el cuerpo `OK`.
- No se usó `spring-boot-starter-actuator` para no agregar una dependencia ni exponer más endpoints de los necesarios para esto.

Qué hay que corregir en la planilla: nada obligatorio, pero si se quiere que figure, va como endpoint de operación aparte de la tabla de CU. Mencionarlo en el README de la Etapa 5 junto con el healthcheck del compose.

**Desvio registrado — RN-04 se puede apagar entera con un interruptor (17/11)**

A pedido del equipo, RN-04 puede dejar de aplicarse en **todos los CU**: CU-02 edita un pedido confirmado o cancelado, CU-03 confirma desde cualquier estado y CU-04 cancela desde cualquier estado, incluido uno ya CANCELADO. Esta detras de la property `deliverygo.pedidos.saltear-validacion-estado`, **apagada por defecto**: con el valor por defecto el flujo es exactamente el de la planilla y nada cambia.

Lo que el interruptor **no** saltea, porque no son reglas de estado:

- El resultado del pago en CU-03: RECHAZADO responde 422, PENDIENTE deja el pedido como esta (desvio del 03/11) y solo APROBADO lo pasa a CONFIRMADO.
- El reembolso en CU-04: un CONFIRMADO que no consigue el reembolso sigue sin cancelarse.
- RN-01, RN-02, RN-03, RN-05 y RN-06, ni la validacion de pedido sin items.

Toda transicion que la maquina de estados no permita queda asentada en el `HistorialEstado` con el motivo marcado (`RN-04 salteada: <desde> a <hacia>`), asi que CU-05 muestra cuales no siguieron la maquina.

Implementacion: `MaquinaEstados.transicionarSinValidar()` —que no valida nada y existe solo para esto— y, en `PedidoService`, `validarEstado()` y `transicionar()`, los dos unicos puntos por los que los CU tocan la maquina de estados. Hay que saltear el chequeo en los dos: si solo se saltea la validacion previa, la operacion pasa el chequeo y despues revienta al aplicar la transicion, y el interruptor no sirve de nada.

Que hay que corregir en la planilla: nada mientras el interruptor este apagado. Si se decide dejarlo prendido, RN-04 y la maquina de estados dejan de ser una regla y pasan a ser documentacion de lo que *deberia* pasar, y hay que rehacer las fichas de CU-02, CU-03 y CU-04 y la tabla de errores, donde `ORDER_INVALID_STATE` ya no saldria nunca. **Hay que avisarle al docente** antes de prenderlo en una entrega o en la demo.

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

## CU-06: listado paginado
SRV-06/API-06 GET /api/v1/orders: listado paginado por cliente. REP-06 findByClienteId(UUID, Pageable); DTO-06 PaginaPedidosResponse/PedidoResumenResponse. Página base cero, tamaño 1..100, orden fechaCreacion DESC e id DESC.
