package org.deliverygo.ordersms.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * INT-03 / INT-04 — Respuesta de Pagos (Eq. 4).
 *
 * <p>El estado se resuelve en la misma llamada: APROBADO o RECHAZADO al cobrar,
 * REEMBOLSADO al devolver.
 */
public record PagoDto(
        UUID paymentId,
        EstadoPago status,
        BigDecimal amount) {
}
