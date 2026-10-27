package org.deliverygo.ordersms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO-04 — Cuerpo de API-03 {@code POST /api/v1/orders/{id}/confirm} (CU-03).
 *
 * <p>El importe no viaja en el cuerpo: lo calcula el pedido con los precios congelados
 * de sus lineas (RN-03). Lo unico que falta para cobrar es con que medio se paga.
 *
 * @param medioPago medio de pago; viaja a Pagos como {@code paymentMethod} (INT-03)
 */
@Schema(description = "Medio con el que se cobra el pedido al confirmarlo (DTO-04)")
public record ConfirmarPedidoRequest(

        @Schema(description = "Medio de pago acordado con Pagos (INT-03)", example = "TARJETA_CREDITO",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "medioPago es obligatorio")
        String medioPago) {
}
