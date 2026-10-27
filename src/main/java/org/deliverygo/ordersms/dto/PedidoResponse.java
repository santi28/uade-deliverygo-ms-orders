package org.deliverygo.ordersms.dto;

import org.deliverygo.ordersms.model.EstadoPedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO-02 — Representacion de un pedido devuelta por API-01 y API-05 (CU-01, CU-05).
 *
 * <p>Expone los modulos ajenos por ID plano (DM-03, DM-09) y el historial de estados,
 * que es lo que consulta CU-05. Lo arma {@code PedidoMapper}.
 */
public record PedidoResponse(
        UUID id,
        UUID clienteId,
        UUID comercioId,
        UUID direccionEntregaId,
        EstadoPedido estado,
        BigDecimal total,
        String moneda,
        UUID pagoId,
        Instant fechaCreacion,
        Instant fechaActualizacion,
        List<ItemResponse> items,
        List<HistorialResponse> historial) {

    /** Linea del pedido con el precio congelado al agregarla (RN-03). */
    public record ItemResponse(
            UUID id,
            UUID productoId,
            String nombreProducto,
            int cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {
    }

    /** Entrada del historial de estados (REL-02), en orden cronologico (REP-04). */
    public record HistorialResponse(
            EstadoPedido estadoAnterior,
            EstadoPedido estadoNuevo,
            Instant fechaCambio,
            String motivo) {
    }
}
