package org.deliverygo.ordersms.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * INT-03 — Respuesta de Pagos al iniciar o consultar un pago
 * ({@code PagoResponse} del OpenAPI del ecosistema v1.4.0).
 *
 * <p>Se toman solo los campos que usa Pedidos; {@code medioPago} y las fechas que
 * tambien publica Pagos se ignoran y Jackson las descarta.
 *
 * @param codigoPago   identificador del pago; es el {@code pagoId} que guarda el pedido
 *                     (DM-09)
 * @param codigoPedido nuestro pedido, como lo devuelve Pagos
 * @param estado       estado del pago; al iniciarlo siempre llega PENDIENTE
 */
public record PagoDto(
        UUID codigoPago,
        UUID codigoPedido,
        EstadoPago estado,
        BigDecimal monto,
        String moneda) {
}
