package org.deliverygo.ordersms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * DTO-03 — Cuerpo de API-02 {@code POST /api/v1/orders/{id}/items} (CU-02).
 *
 * <p>Da de alta lineas nuevas. Un producto que ya esta en el pedido se cambia con
 * API-02b y se saca con API-02c: aca es VALIDATION_ERROR, para que un POST repetido no
 * duplique ni acumule cantidad sin que el cliente lo haya pedido.
 *
 * <p>La cantidad tiene que ser mayor a cero: para quitar una linea esta el DELETE.
 *
 * @param items lineas a agregar; al menos una
 */
@Schema(description = "Lineas nuevas de un pedido en BORRADOR (DTO-03)")
public record AgregarItemsPedidoRequest(

        @Schema(description = "Lineas a agregar al pedido; al menos una",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "items no puede estar vacio")
        @Valid
        List<LineaItem> items) {

    /**
     * Linea pedida por el cliente. El precio no viaja en el request: lo resuelve
     * Catalogo (INT-01) y lo congela {@code ItemPedido} (RN-03).
     *
     * @param productoId producto de Catalogo; tiene que ser del comercio del pedido (RN-02)
     * @param cantidad   unidades de la linea; mayor a cero
     */
    @Schema(description = "Producto y cantidad de una linea nueva")
    public record LineaItem(

            @Schema(description = "Producto de Catalogo (INT-01)",
                    example = "33333333-3333-3333-3333-333333333333",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "productoId es obligatorio")
            UUID productoId,

            @Schema(description = "Unidades de la linea; para quitarla se usa DELETE",
                    example = "2", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive(message = "cantidad tiene que ser mayor a cero")
            int cantidad) {
    }
}
