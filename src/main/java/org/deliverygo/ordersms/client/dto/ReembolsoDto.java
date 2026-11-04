package org.deliverygo.ordersms.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * INT-04 — Respuesta de {@code POST /api/v1/payments/{codigoPago}/refund} en CU-04
 * ({@code ReembolsoResponse} del OpenAPI del ecosistema v1.4.0).
 *
 * <p>Ojo con {@code estado}: es el estado <strong>del reembolso</strong> (APROBADO
 * cuando se concedio), no el del pago. El contrato de PLAN.md esperaba REEMBOLSADO
 * mirando el pago; contra la API real lo que habilita pasar a CANCELADO es que este
 * reembolso quede aprobado.
 *
 * @param codigoReembolso identificador del reembolso en Pagos
 * @param montoDisponible saldo del pago que queda sin reembolsar
 */
public record ReembolsoDto(
        UUID codigoReembolso,
        UUID codigoPago,
        BigDecimal monto,
        String estado,
        BigDecimal montoDisponible) {

    /** Estado con el que Pagos informa un reembolso concedido. */
    private static final String APROBADO = "APROBADO";

    /** INT-04 — El reembolso se concedio; solo asi CU-04 pasa el pedido a CANCELADO. */
    public boolean aprobado() {
        return APROBADO.equalsIgnoreCase(estado);
    }
}
