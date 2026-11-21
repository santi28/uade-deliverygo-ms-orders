package org.deliverygo.ordersms.client;

import org.deliverygo.ordersms.client.dto.PagoDto;
import org.deliverygo.ordersms.client.dto.ReembolsoDto;
import org.deliverygo.ordersms.client.dto.SolicitudPagoDto;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * INT-03 / INT-04 / INT-05 — Cliente de Pagos (Eq. 4).
 *
 * <p>INT-03 {@code POST /api/v1/payments} en CU-03 e INT-04
 * {@code POST /api/v1/payments/{codigoPago}/refund} en CU-04. Las dos llamadas son
 * idempotentes por una clave de control derivada del pedido.
 *
 * <p>Pagos autoriza en una llamada aparte que no hacemos nosotros (su {@code /approve}),
 * asi que {@link #iniciarPago(SolicitudPagoDto)} devuelve el pago en PENDIENTE: esa
 * llamada no confirma el pedido.
 */
public interface PagosClient {

    /** INT-03 — Inicia el pago del pedido; nace en PENDIENTE. */
    PagoDto iniciarPago(SolicitudPagoDto solicitud);

    /**
     * INT-04 — Pide el reembolso total del pago; solo un reembolso aprobado habilita
     * pasar el pedido a CANCELADO.
     *
     * @param codigoPago pago a reembolsar
     * @param pedidoId   pedido que se cancela; de el sale la clave de idempotencia
     * @param monto      total del pedido; Pedidos no hace reembolsos parciales
     */
    ReembolsoDto reembolsar(UUID codigoPago, UUID pedidoId, BigDecimal monto);

    /**
     * INT-05 — Consulta el pago en Pagos; CU-09 aplica lo que responde aca y no lo que
     * diga el aviso que recibe.
     *
     * @return el pago, o vacio si Pagos no lo conoce (404)
     */
    Optional<PagoDto> consultarPago(UUID codigoPago);
}
