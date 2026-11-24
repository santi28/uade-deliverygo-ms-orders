package org.deliverygo.ordersms.client.dto;

/**
 * INT-06 — Respuesta de Logistica al pedir la entrega ({@code EntregaResponse} del
 * OpenAPI del ecosistema v1.4.0).
 *
 * <p>Se toman solo los campos que Pedidos registra en el historial; el estado de la
 * entrega no se persiste en {@code Pedido}, igual que el del pago (DM-09).
 *
 * @param id     identificador de la entrega en Logistica; numerico, lo define Logistica
 * @param estado ASIGNADA (201) o PENDIENTE_ASIGNACION (202, no hay repartidores)
 */
public record EntregaDto(
        Long id,
        String estado) {
}
