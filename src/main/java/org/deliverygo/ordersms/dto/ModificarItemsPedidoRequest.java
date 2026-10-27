package org.deliverygo.ordersms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

/**
 * DTO-03 — Cuerpo de API-02 {@code POST /api/v1/orders/{id}/items} (CU-02).
 *
 * <p>Una sola operacion declara el estado deseado de cada linea: la cantidad enviada
 * reemplaza la que tenia el pedido, no se suma. {@code cantidad = 0} quita la linea, y
 * una cantidad negativa es VALIDATION_ERROR (400) resuelto por Bean Validation, antes de
 * llegar al service.
 *
 * <p>Los productos que no se mencionan quedan como estaban.
 *
 * @param items lineas a aplicar; al menos una
 */
@Schema(description = "Altas, cambios y bajas de lineas de un pedido en BORRADOR (DTO-03)")
public record ModificarItemsPedidoRequest(

        @Schema(description = "Lineas a aplicar sobre el pedido; al menos una",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "items no puede estar vacio")
        @Valid
        List<LineaItem> items) {

    /**
     * Linea pedida por el cliente. El precio no viaja en el request: lo resuelve
     * Catalogo (INT-01) y lo congela {@code ItemPedido} (RN-03).
     *
     * @param productoId producto de Catalogo; tiene que ser del comercio del pedido (RN-02)
     * @param cantidad   cantidad final de la linea; 0 la quita del pedido
     */
    @Schema(description = "Cantidad final de un producto en el pedido; 0 lo quita")
    public record LineaItem(

            @Schema(description = "Producto de Catalogo (INT-01)",
                    example = "33333333-3333-3333-3333-333333333333",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "productoId es obligatorio")
            UUID productoId,

            @Schema(description = "Cantidad final de la linea; 0 quita el producto del pedido",
                    example = "2", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
            @PositiveOrZero(message = "cantidad no puede ser negativa")
            int cantidad) {
    }
}
