package org.deliverygo.ordersms.client;

import org.deliverygo.ordersms.client.dto.PagoDto;
import org.deliverygo.ordersms.client.dto.SolicitudPagoDto;

import java.util.UUID;

/**
 * INT-03 / INT-04 — Cliente de Pagos (Eq. 4).
 *
 * <p>INT-03 {@code POST /api/v1/payments} en CU-03 e INT-04
 * {@code POST /api/v1/payments/{pagoId}/refund} en CU-04. Ambas llamadas son
 * idempotentes por {@code pedidoId}.
 */
public interface PagosClient {

    /** INT-03 — Cobra el pedido; el estado llega resuelto en la misma respuesta. */
    PagoDto cobrar(SolicitudPagoDto solicitud);

    /** INT-04 — Pide el reembolso; solo REEMBOLSADO habilita pasar a CANCELADO. */
    PagoDto reembolsar(UUID pagoId, UUID pedidoId);
}
