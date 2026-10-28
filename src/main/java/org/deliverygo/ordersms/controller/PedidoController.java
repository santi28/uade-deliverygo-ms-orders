package org.deliverygo.ordersms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.deliverygo.ordersms.dto.AgregarItemsPedidoRequest;
import org.deliverygo.ordersms.dto.ConfirmarPedidoRequest;
import org.deliverygo.ordersms.dto.CrearPedidoRequest;
import org.deliverygo.ordersms.dto.ErrorResponse;
import org.deliverygo.ordersms.dto.ModificarCantidadItemRequest;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.service.PedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

import static org.deliverygo.ordersms.config.OpenApiConfig.REF_EJEMPLOS;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * API-01 a API-05 — Endpoints HTTP de Pedidos.
 *
 * <p>El controller solo traduce HTTP: las reglas de negocio viven en
 * {@link PedidoService}.
 *
 * <p>La identidad del cliente llega en {@code X-Cliente-Id}. Es provisorio: en la Etapa 4
 * se reemplaza por el subject del JWT que emite Usuarios (RN-01, INT-02).
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Pedidos", description = "Ciclo funcional de la compra (CU-01 a CU-05)")
public class PedidoController {

    /** Identidad del cliente; provisoria hasta el JWT de la Etapa 4. */
    public static final String HEADER_CLIENTE = "X-Cliente-Id";

    private static final String DESCRIPCION_CLIENTE =
            "Identidad del cliente autenticado (RN-01). Provisoria: la reemplaza el JWT en la Etapa 4.";

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    /**
     * API-01 — {@code POST /api/v1/orders} (CU-01): crea el pedido en BORRADOR o devuelve
     * el borrador abierto del cliente.
     *
     * <p>Responde 201 con la cabecera {@code Location} del recurso.
     */
    @Operation(summary = "CU-01 — Crear pedido o borrador",
            description = """
                    Crea un pedido en estado BORRADOR, sin items y con total cero.

                    Valida contra Usuarios (INT-02) que el cliente exista y este activo (RN-01) y que \
                    la direccion de entrega sea suya (RN-06). Si el cliente ya tiene un borrador abierto \
                    del mismo comercio se devuelve ese en vez de crear otro (REP-01).

                    Las lineas se cargan despues con CU-02.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido creado, o borrador abierto ya existente"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — falta un dato, el cliente no esta"
                    + " registrado o activo (RN-01), o la direccion no es suya (RN-06)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VALIDATION_ERROR", ref = REF_EJEMPLOS + "VALIDATION_ERROR"))),
            @ApiResponse(responseCode = "422", description = "MIXED_MERCHANT — el cliente ya tiene un borrador"
                    + " abierto de otro comercio (RN-02)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "MIXED_MERCHANT", ref = REF_EJEMPLOS + "MIXED_MERCHANT"))),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE — Usuarios no responde (INT-02)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "DEPENDENCY_UNAVAILABLE", ref = REF_EJEMPLOS + "DEPENDENCY_UNAVAILABLE")))
    })
    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(
            @RequestHeader(HEADER_CLIENTE)
            @Parameter(description = DESCRIPCION_CLIENTE, example = "11111111-1111-1111-1111-111111111111")
            UUID clienteId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Comercio y direccion de entrega del pedido",
                    content = @Content(examples = @ExampleObject(name = "Pedido en la pizzeria",
                            description = "Cliente activo y direccion suya, segun los datos de los mocks",
                            value = """
                                    {
                                      "comercioId": "99999999-9999-9999-9999-999999999999",
                                      "direccionEntregaId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                                    }""")))
            @Valid @RequestBody CrearPedidoRequest request,
            UriComponentsBuilder uriBuilder) {

        PedidoResponse pedido = pedidoService.crearPedido(clienteId, request);
        URI ubicacion = uriBuilder.path("/api/v1/orders/{id}").buildAndExpand(pedido.id()).toUri();
        return ResponseEntity.created(ubicacion).body(pedido);
    }

    /**
     * API-02 — {@code POST /api/v1/orders/{id}/items} (CU-02): agrega lineas nuevas al
     * pedido.
     *
     * <p>Responde 200 con el pedido completo y no 201: lo que el cliente necesita despues
     * de cada cambio es el pedido recalculado.
     */
    @Operation(summary = "CU-02 — Agregar items",
            description = """
                    Agrega lineas nuevas a un pedido en BORRADOR, recalcula el total y asienta la \
                    edicion en el historial.

                    Un producto que ya esta en el pedido se rechaza con 400: la cantidad de una linea \
                    existente se cambia con PUT y la linea se saca con DELETE. Asi repetir el POST no \
                    acumula cantidad sin que el cliente lo haya pedido.

                    Valida contra Catalogo (INT-01) que el producto exista, sea del comercio del pedido \
                    (RN-02) y este disponible (RN-05). El precio lo pone Catalogo y queda congelado en \
                    la linea (RN-03): no viaja en el cuerpo.

                    Un pedido que no esta en BORRADOR no se edita (RN-04).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido actualizado, con el total recalculado"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — lista vacia, cantidad menor o igual a cero, producto"
                                + " repetido, producto inexistente en Catalogo, o producto que ya esta"
                                + " en el pedido",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VALIDATION_ERROR", ref = REF_EJEMPLOS + "VALIDATION_ERROR"))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — el pedido no existe o es de otro cliente",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_NOT_FOUND", ref = REF_EJEMPLOS + "ORDER_NOT_FOUND"))),
            @ApiResponse(responseCode = "409", description = "ORDER_INVALID_STATE — el pedido no esta en BORRADOR (RN-04)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_INVALID_STATE", ref = REF_EJEMPLOS + "ORDER_INVALID_STATE"))),
            @ApiResponse(responseCode = "422", description = "MIXED_MERCHANT — producto de otro comercio (RN-02);"
                                + " PRODUCT_UNAVAILABLE — producto no disponible (RN-05)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                        @ExampleObject(name = "MIXED_MERCHANT", ref = REF_EJEMPLOS + "MIXED_MERCHANT"),
                                        @ExampleObject(name = "PRODUCT_UNAVAILABLE", ref = REF_EJEMPLOS + "PRODUCT_UNAVAILABLE") })),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE — Catalogo no responde (INT-01)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "DEPENDENCY_UNAVAILABLE", ref = REF_EJEMPLOS + "DEPENDENCY_UNAVAILABLE")))
    })
    @PostMapping("/{id}/items")
    public PedidoResponse agregarItems(
            @PathVariable
            @Parameter(description = "Identificador del pedido") UUID id,
            @RequestHeader(HEADER_CLIENTE)
            @Parameter(description = DESCRIPCION_CLIENTE, example = "11111111-1111-1111-1111-111111111111")
            UUID clienteId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Lineas a agregar; la cantidad tiene que ser mayor a cero",
                    content = @Content(examples = {
                            @ExampleObject(name = "1 - Dos lineas",
                                    description = "Agrega 2 pizzas y 3 empanadas: total 20600",
                                    value = """
                                            {
                                              "items": [
                                                { "productoId": "33333333-3333-3333-3333-333333333333", \
                                "cantidad": 2 },
                                                { "productoId": "44444444-4444-4444-4444-444444444444", \
                                "cantidad": 3 }
                                              ]
                                            }"""),
                            @ExampleObject(name = "2 - Producto de otro comercio (RN-02)",
                                    description = "La gaseosa es del kiosco: responde 422 MIXED_MERCHANT",
                                    value = """
                                            {
                                              "items": [
                                                { "productoId": "55555555-5555-5555-5555-555555555555", \
                                "cantidad": 1 }
                                              ]
                                            }"""),
                            @ExampleObject(name = "3 - Producto sin stock (RN-05)",
                                    description = "El helado no esta disponible: responde 422 PRODUCT_UNAVAILABLE",
                                    value = """
                                            {
                                              "items": [
                                                { "productoId": "66666666-6666-6666-6666-666666666666", \
                                "cantidad": 1 }
                                              ]
                                            }"""),
                            @ExampleObject(name = "4 - Cantidad invalida",
                                    description = "Cero o negativa: responde 400 VALIDATION_ERROR",
                                    value = """
                                            {
                                              "items": [
                                                { "productoId": "33333333-3333-3333-3333-333333333333", \
                                "cantidad": 0 }
                                              ]
                                            }""") }))
            @Valid @RequestBody AgregarItemsPedidoRequest request) {
        return pedidoService.agregarItems(id, clienteId, request);
    }

    /**
     * API-02b — {@code PUT /api/v1/orders/{id}/items/{productoId}} (CU-02): reemplaza la
     * cantidad de una linea.
     *
     * <p>Es un PUT porque deja la linea en el estado que declara el cuerpo, sin importar
     * cuantas veces se repita la llamada.
     */
    @Operation(summary = "CU-02 — Modificar la cantidad de un item",
            description = """
                    Reemplaza la cantidad de una linea de un pedido en BORRADOR: la cantidad enviada no \
                    se suma a la que habia. Despues recalcula el total y asienta la edicion en el \
                    historial.

                    La linea tiene que existir; si el producto no esta en el pedido responde 400, porque \
                    el alta es el POST. Para quitarla esta el DELETE: la cantidad no puede ser cero.

                    Vuelve a preguntar a Catalogo antes de subir la cantidad (RN-02, RN-05). El precio \
                    sigue siendo el congelado al agregar la linea (RN-03); si cambio, lo detecta CU-03 al \
                    confirmar.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido actualizado, con el total recalculado"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — cantidad menor o igual a cero, o el producto no esta"
                                + " en el pedido",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VALIDATION_ERROR", ref = REF_EJEMPLOS + "VALIDATION_ERROR"))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — el pedido no existe o es de otro cliente",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_NOT_FOUND", ref = REF_EJEMPLOS + "ORDER_NOT_FOUND"))),
            @ApiResponse(responseCode = "409", description = "ORDER_INVALID_STATE — el pedido no esta en BORRADOR (RN-04)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_INVALID_STATE", ref = REF_EJEMPLOS + "ORDER_INVALID_STATE"))),
            @ApiResponse(responseCode = "422", description = "MIXED_MERCHANT — producto de otro comercio (RN-02);"
                                + " PRODUCT_UNAVAILABLE — producto no disponible (RN-05)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                        @ExampleObject(name = "MIXED_MERCHANT", ref = REF_EJEMPLOS + "MIXED_MERCHANT"),
                                        @ExampleObject(name = "PRODUCT_UNAVAILABLE", ref = REF_EJEMPLOS + "PRODUCT_UNAVAILABLE") })),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE — Catalogo no responde (INT-01)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "DEPENDENCY_UNAVAILABLE", ref = REF_EJEMPLOS + "DEPENDENCY_UNAVAILABLE")))
    })
    @PutMapping("/{id}/items/{productoId}")
    public PedidoResponse modificarCantidadItem(
            @PathVariable
            @Parameter(description = "Identificador del pedido") UUID id,
            @PathVariable
            @Parameter(description = "Producto de la linea a modificar",
                    example = "33333333-3333-3333-3333-333333333333") UUID productoId,
            @RequestHeader(HEADER_CLIENTE)
            @Parameter(description = DESCRIPCION_CLIENTE, example = "11111111-1111-1111-1111-111111111111")
            UUID clienteId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Cantidad final de la linea; mayor a cero",
                    content = @Content(examples = {
                            @ExampleObject(name = "1 - Cinco unidades", value = """
                                            { "cantidad": 5 }"""),
                            @ExampleObject(name = "2 - Cantidad invalida",
                                    description = "Cero o negativa: responde 400 VALIDATION_ERROR",
                                    value = """
                                            { "cantidad": 0 }""") }))
            @Valid @RequestBody ModificarCantidadItemRequest request) {
        return pedidoService.modificarCantidadItem(id, clienteId, productoId, request);
    }

    /**
     * API-02c — {@code DELETE /api/v1/orders/{id}/items/{productoId}} (CU-02): saca una
     * linea del pedido.
     *
     * <p>Responde 200 con el pedido recalculado y no 204: despues de sacar una linea lo
     * que hay que mostrar es el total nuevo.
     */
    @Operation(summary = "CU-02 — Eliminar un item",
            description = """
                    Saca una linea de un pedido en BORRADOR, recalcula el total y asienta la edicion en \
                    el historial.

                    Es idempotente: si el producto no esta en el pedido responde 200 con el pedido sin \
                    cambios y sin dejar entrada en el historial, para que reintentar el DELETE no falle.

                    No consulta Catalogo: un producto que el comercio dio de baja igual se tiene que \
                    poder sacar del borrador.

                    Un pedido que no esta en BORRADOR no se edita (RN-04).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido actualizado, con el total recalculado"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — identificador mal formado o falta la cabecera"
                                + " X-Cliente-Id",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VALIDATION_ERROR", ref = REF_EJEMPLOS + "VALIDATION_ERROR"))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — el pedido no existe o es de otro cliente",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_NOT_FOUND", ref = REF_EJEMPLOS + "ORDER_NOT_FOUND"))),
            @ApiResponse(responseCode = "409", description = "ORDER_INVALID_STATE — el pedido no esta en BORRADOR (RN-04)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_INVALID_STATE", ref = REF_EJEMPLOS + "ORDER_INVALID_STATE")))
    })
    @DeleteMapping("/{id}/items/{productoId}")
    public PedidoResponse quitarItem(
            @PathVariable
            @Parameter(description = "Identificador del pedido") UUID id,
            @PathVariable
            @Parameter(description = "Producto de la linea a sacar",
                    example = "44444444-4444-4444-4444-444444444444") UUID productoId,
            @RequestHeader(HEADER_CLIENTE)
            @Parameter(description = DESCRIPCION_CLIENTE, example = "11111111-1111-1111-1111-111111111111")
            UUID clienteId) {
        return pedidoService.quitarItem(id, clienteId, productoId);
    }

    /**
     * API-03 — {@code POST /api/v1/orders/{id}/confirm} (CU-03): cobra el pedido y lo
     * pasa a CONFIRMADO.
     */
    @Operation(summary = "CU-03 — Confirmar pedido",
            description = """
                    Revalida el pedido y lo cobra. Antes de pagar vuelve a preguntar a Usuarios por el \
                    cliente y la direccion (RN-01, RN-06, INT-02) y a Catalogo por la disponibilidad y \
                    el precio de cada linea (RN-05, RN-03, INT-01). Un pedido sin lineas no se confirma.

                    Con el cobro APROBADO (INT-03) se guarda el pagoId y el pedido pasa a CONFIRMADO en \
                    la misma transaccion. Con el cobro RECHAZADO el pedido sigue en BORRADOR, el intento \
                    queda en el historial y la respuesta es 422.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido confirmado y asociado al pago"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — falta el medio de pago, el pedido"
                    + " no tiene items, o el cliente o la direccion ya no son validos (RN-01, RN-06)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VALIDATION_ERROR", ref = REF_EJEMPLOS + "VALIDATION_ERROR"))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — el pedido no existe o es de otro"
                    + " cliente",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_NOT_FOUND", ref = REF_EJEMPLOS + "ORDER_NOT_FOUND"))),
            @ApiResponse(responseCode = "409", description = "ORDER_INVALID_STATE — el pedido no esta en BORRADOR"
                    + " (RN-04)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_INVALID_STATE", ref = REF_EJEMPLOS + "ORDER_INVALID_STATE"))),
            @ApiResponse(responseCode = "422", description = "PAYMENT_REJECTED — Pagos rechazo el cobro;"
                    + " PRICE_CHANGED — cambio un precio (RN-03); PRODUCT_UNAVAILABLE — una linea ya no esta"
                    + " disponible (RN-05); MIXED_MERCHANT — una linea ya no es del comercio (RN-02)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                            @ExampleObject(name = "PAYMENT_REJECTED", ref = REF_EJEMPLOS + "PAYMENT_REJECTED"),
                            @ExampleObject(name = "PRICE_CHANGED", ref = REF_EJEMPLOS + "PRICE_CHANGED"),
                            @ExampleObject(name = "PRODUCT_UNAVAILABLE", ref = REF_EJEMPLOS + "PRODUCT_UNAVAILABLE"),
                            @ExampleObject(name = "MIXED_MERCHANT", ref = REF_EJEMPLOS + "MIXED_MERCHANT")
                            })),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE — Usuarios, Catalogo o Pagos"
                    + " no responde (INT-01..INT-03); el pedido sigue en BORRADOR",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "DEPENDENCY_UNAVAILABLE", ref = REF_EJEMPLOS + "DEPENDENCY_UNAVAILABLE")))
    })
    @PostMapping("/{id}/confirm")
    public PedidoResponse confirmarPedido(
            @PathVariable
            @Parameter(description = "Identificador del pedido") UUID id,
            @RequestHeader(HEADER_CLIENTE)
            @Parameter(description = DESCRIPCION_CLIENTE, example = "11111111-1111-1111-1111-111111111111")
            UUID clienteId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Medio con el que Pagos cobra el pedido (INT-03)",
                    content = @Content(examples = {
                            @ExampleObject(name = "1 - Tarjeta de credito",
                                    description = "El mock aprueba los cobros de menos de 100000",
                                    value = """
                                            { "medioPago": "TARJETA_CREDITO" }"""),
                            @ExampleObject(name = "2 - Efectivo",
                                    value = """
                                            { "medioPago": "EFECTIVO" }""") }))
            @Valid @RequestBody ConfirmarPedidoRequest request) {
        return pedidoService.confirmarPedido(id, clienteId, request);
    }

    /**
     * API-04 — {@code DELETE /api/v1/orders/{id}} (CU-04): cancela el pedido,
     * reembolsando si ya estaba confirmado.
     *
     * <p>Cancelar es el borrado logico del pedido, asi que el DELETE va sobre el pedido
     * mismo y no sobre un sub-recurso de accion: la fila nunca se borra, pasa a CANCELADO
     * y queda consultable con CU-05, que es lo que el historial necesita.
     *
     * <p>No lleva cuerpo: el motivo lo arma el service segun desde donde se cancele.
     * Responde 200 con el pedido ya cancelado, y no 204, porque el cliente tiene que ver
     * en que estado quedo y si hubo reembolso.
     */
    @Operation(summary = "CU-04 — Cancelar pedido",
            description = """
                    Cancela el pedido: es su borrado logico, el pedido pasa a CANCELADO y sigue siendo \
                    consultable con CU-05.

                    Un BORRADOR se cancela sin reembolso. Un pedido CONFIRMADO exige reembolso: se pide \
                    a Pagos (INT-04) y solo pasa a CANCELADO si responde REEMBOLSADO; si Pagos no \
                    responde, el pedido queda como estaba.

                    Desde cualquier otro estado la cancelacion no esta permitida (RN-04).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido cancelado"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — identificador mal formado o falta"
                    + " la cabecera X-Cliente-Id",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VALIDATION_ERROR", ref = REF_EJEMPLOS + "VALIDATION_ERROR"))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — el pedido no existe o es de otro"
                    + " cliente",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_NOT_FOUND", ref = REF_EJEMPLOS + "ORDER_NOT_FOUND"))),
            @ApiResponse(responseCode = "409", description = "ORDER_INVALID_STATE — el pedido no se puede cancelar"
                    + " desde su estado actual (RN-04)",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_INVALID_STATE", ref = REF_EJEMPLOS + "ORDER_INVALID_STATE"))),
            @ApiResponse(responseCode = "422", description = "PAYMENT_REJECTED — Pagos no reembolso; el pedido"
                    + " sigue CONFIRMADO",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "PAYMENT_REJECTED", ref = REF_EJEMPLOS + "PAYMENT_REJECTED"))),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE — Pagos no responde (INT-04);"
                    + " el pedido no cambia de estado",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "DEPENDENCY_UNAVAILABLE", ref = REF_EJEMPLOS + "DEPENDENCY_UNAVAILABLE")))
    })
    @DeleteMapping("/{id}")
    public PedidoResponse cancelarPedido(
            @PathVariable
            @Parameter(description = "Identificador del pedido") UUID id,
            @RequestHeader(HEADER_CLIENTE)
            @Parameter(description = DESCRIPCION_CLIENTE, example = "11111111-1111-1111-1111-111111111111")
            UUID clienteId) {
        return pedidoService.cancelarPedido(id, clienteId);
    }

    /**
     * API-05 — {@code GET /api/v1/orders/{id}} (CU-05): devuelve el pedido con sus items y
     * su historial de estados.
     *
     * <p>Un pedido de otro cliente responde 404 ORDER_NOT_FOUND.
     */
    @Operation(summary = "CU-05 — Consultar pedido y estado",
            description = """
                    Devuelve el pedido con sus items y su historial de estados en orden cronologico \
                    (REP-03, REP-04).

                    Un pedido de otro cliente responde igual que uno inexistente, para no revelar que \
                    pedidos existen.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — identificador mal formado o falta"
                    + " la cabecera X-Cliente-Id",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VALIDATION_ERROR", ref = REF_EJEMPLOS + "VALIDATION_ERROR"))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — el pedido no existe o es de otro"
                    + " cliente",
                    content = @Content(mediaType = APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "ORDER_NOT_FOUND", ref = REF_EJEMPLOS + "ORDER_NOT_FOUND")))
    })
    @GetMapping("/{id}")
    public PedidoResponse consultarPedido(
            @PathVariable
            @Parameter(description = "Identificador del pedido") UUID id,
            @RequestHeader(HEADER_CLIENTE)
            @Parameter(description = DESCRIPCION_CLIENTE, example = "11111111-1111-1111-1111-111111111111")
            UUID clienteId) {
        return pedidoService.consultarPedido(id, clienteId);
    }
}
