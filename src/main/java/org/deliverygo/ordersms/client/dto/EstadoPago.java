package org.deliverygo.ordersms.client.dto;

/**
 * INT-03 / INT-04 — Estados de un pago en el modulo de Pagos (Eq. 4).
 *
 * <p>Son los cuatro del {@code PagoResponse.estado} del OpenAPI del ecosistema
 * (v1.4.0). No se persisten en {@code Pedido}: el pedido guarda solo {@code pagoId}
 * (DM-09).
 *
 * <p>{@code PENDIENTE} es el estado con el que nace todo pago: Pagos responde 201
 * PENDIENTE y resuelve la autorizacion en una llamada aparte que no hacemos nosotros.
 * Un valor que no este en esta lista rompe la deserializacion a proposito y el cliente
 * HTTP lo traduce a 503: es preferible cortar a confirmar un pedido interpretando mal
 * la respuesta de Pagos.
 */
public enum EstadoPago {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    REEMBOLSADO
}
