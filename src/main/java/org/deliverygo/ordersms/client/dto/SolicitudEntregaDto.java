package org.deliverygo.ordersms.client.dto;

import java.util.UUID;

/**
 * INT-06 — Datos minimos que Pedidos le pasa a Logistica para pedir la entrega (CU-10).
 *
 * <p>No es todavia el {@code AsignarEntregaRequest} del OpenAPI del ecosistema: ese pide
 * {@code pedidoId} y {@code clienteId} como {@code int64} y las direcciones de retiro y
 * entrega en texto. Aca viajan nuestros ids y la direccion por referencia, que es lo que
 * Pedidos tiene. Se ajusta cuando se cierre el contrato con Logistica (PLAN.md, Etapa 6).
 *
 * @param direccionEntregaId direccion del pedido; Pedidos guarda solo la referencia
 */
public record SolicitudEntregaDto(
        UUID pedidoId,
        UUID clienteId,
        Long comercioId,
        UUID direccionEntregaId) {
}
