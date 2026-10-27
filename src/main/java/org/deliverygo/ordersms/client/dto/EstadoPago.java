package org.deliverygo.ordersms.client.dto;

/**
 * INT-03 / INT-04 — Estados que devuelve Pagos (Eq. 4) segun el contrato acordado.
 *
 * <p>No se persiste en {@code Pedido}: el pedido guarda solo {@code pagoId} (DM-09).
 */
public enum EstadoPago {
    APROBADO,
    RECHAZADO,
    REEMBOLSADO
}
