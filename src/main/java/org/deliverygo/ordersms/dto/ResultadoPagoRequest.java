package org.deliverygo.ordersms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO-09 — Cuerpo de API-09 {@code POST /api/v1/orders/{id}/payment-result} (CU-09).
 *
 * <p>Solo identifica el pago: el estado, el importe y la moneda no viajan en el aviso
 * porque CU-09 no se los cree; los lee de Pagos con INT-05.
 *
 * @param codigoPago pago del que se avisa; tiene que ser el {@code pagoId} del pedido
 */
@Schema(description = "Aviso de que un pago del pedido cambio de estado (DTO-09)")
public record ResultadoPagoRequest(

        @Schema(description = "Codigo del pago en Pagos (INT-05)", example = "7b1e2f3a-4c5d-4e6f-8a9b-0c1d2e3f4a5b",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "codigoPago es obligatorio")
        UUID codigoPago) {
}
