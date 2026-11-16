package org.deliverygo.ordersms.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.deliverygo.ordersms.model.EstadoPedido;
/** DTO-06 — Resumen sin colecciones para CU-06. */
public record PedidoResumenResponse(UUID id, Long comercioId, EstadoPedido estado,
        BigDecimal total, String moneda, Instant fechaCreacion) {}
