package org.deliverygo.ordersms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.deliverygo.ordersms.dto.CrearPedidoRequest;
import org.deliverygo.ordersms.dto.ErrorResponse;
import org.deliverygo.ordersms.dto.ModificarItemsPedidoRequest;
import org.deliverygo.ordersms.dto.PedidoResponse;
import org.deliverygo.ordersms.service.PedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * API-01, API-02 y API-05 — Endpoints HTTP de Pedidos.
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
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "MIXED_MERCHANT — el cliente ya tiene un borrador"
                    + " abierto de otro comercio (RN-02)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE — Usuarios no responde (INT-02)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(
            @RequestHeader(HEADER_CLIENTE)
            @Parameter(description = DESCRIPCION_CLIENTE, example = "11111111-1111-1111-1111-111111111111")
            UUID clienteId,
            @Valid @RequestBody CrearPedidoRequest request,
            UriComponentsBuilder uriBuilder) {

        PedidoResponse pedido = pedidoService.crearPedido(clienteId, request);
        URI ubicacion = uriBuilder.path("/api/v1/orders/{id}").buildAndExpand(pedido.id()).toUri();
        return ResponseEntity.created(ubicacion).body(pedido);
    }

    /**
     * API-02 — {@code POST /api/v1/orders/{id}/items} (CU-02): aplica altas, cambios y
     * bajas de lineas sobre un pedido en BORRADOR.
     *
     * <p>Responde 200 con el pedido ya recalculado: no crea un recurso nuevo, modifica el
     * pedido.
     */
    @Operation(summary = "CU-02 — Agregar, modificar o eliminar items",
            description = """
                    Aplica sobre un pedido en BORRADOR la cantidad final de cada producto enviado: \
                    agrega la linea si no estaba, la reemplaza si ya estaba y la quita con cantidad 0. \
                    Los productos que el cuerpo no menciona quedan como estaban. Al final recalcula el \
                    total y asienta la edicion en el historial.

                    Valida contra Catalogo (INT-01) que el producto exista, sea del comercio del pedido \
                    (RN-02) y este disponible (RN-05). El precio lo pone Catalogo y queda congelado en \
                    la linea (RN-03): no viaja en el cuerpo.

                    Un pedido que no esta en BORRADOR no se edita (RN-04).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido actualizado, con el total recalculado"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — lista vacia, cantidad negativa,"
                    + " producto repetido o producto inexistente en Catalogo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — el pedido no existe o es de otro"
                    + " cliente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ORDER_INVALID_STATE — el pedido no esta en BORRADOR"
                    + " (RN-04)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "MIXED_MERCHANT — producto de otro comercio (RN-02);"
                    + " PRODUCT_UNAVAILABLE — producto no disponible (RN-05)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE — Catalogo no responde (INT-01)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/items")
    public PedidoResponse gestionarItems(
            @PathVariable
            @Parameter(description = "Identificador del pedido") UUID id,
            @RequestHeader(HEADER_CLIENTE)
            @Parameter(description = DESCRIPCION_CLIENTE, example = "11111111-1111-1111-1111-111111111111")
            UUID clienteId,
            @Valid @RequestBody ModificarItemsPedidoRequest request) {
        return pedidoService.gestionarItems(id, clienteId, request);
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
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND — el pedido no existe o es de otro"
                    + " cliente",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
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
