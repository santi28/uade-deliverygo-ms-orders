package org.deliverygo.ordersms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Positive;

/**
 * DTO-05 — Cuerpo de API-02b {@code PUT /api/v1/orders/{id}/items/{productoId}} (CU-02).
 *
 * <p>La cantidad enviada reemplaza la que tenia la linea, no se suma. Tiene que ser
 * mayor a cero: para quitar la linea esta API-02c ({@code DELETE}).
 *
 * <p>El producto no viaja en el cuerpo: es parte de la URL, porque la linea es el
 * recurso que se modifica. El precio sigue siendo el congelado al agregarla (RN-03).
 *
 * @param cantidad cantidad final de la linea; mayor a cero
 */
@Schema(description = "Cantidad final de una linea del pedido (DTO-05)")
public record ModificarCantidadItemRequest(

        @Schema(description = "Cantidad final de la linea; reemplaza la anterior, no se suma",
                example = "5", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive(message = "cantidad tiene que ser mayor a cero")
        int cantidad) {
}
