package org.deliverygo.ordersms.client.dto;

import java.math.BigDecimal;

/**
 * INT-04 — Cuerpo de {@code POST /api/v1/payments/{codigoPago}/refund} en CU-04
 * ({@code ReembolsoRequest} del OpenAPI del ecosistema v1.4.0).
 *
 * <p>Pagos exige los tres campos. Se reembolsa el total del pedido: Pedidos no hace
 * reembolsos parciales. {@code claveControl} lleva prefijo para no colisionar con la
 * clave del cobro del mismo pedido.
 */
public record SolicitudReembolsoDto(
        BigDecimal monto,
        String motivo,
        String claveControl) {
}
