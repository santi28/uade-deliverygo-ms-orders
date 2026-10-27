package org.deliverygo.ordersms.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * INT-03 — Cuerpo de {@code POST /api/v1/payments} (CU-03).
 *
 * <p>Viaja con el header {@code Idempotency-Key: <pedidoId>} para que reintentar la
 * confirmacion no genere dos cobros.
 */
public record SolicitudPagoDto(
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String paymentMethod) {
}
