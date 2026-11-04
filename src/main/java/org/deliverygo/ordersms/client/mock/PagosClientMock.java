package org.deliverygo.ordersms.client.mock;

import org.deliverygo.ordersms.client.PagosClient;
import org.deliverygo.ordersms.client.dto.EstadoPago;
import org.deliverygo.ordersms.client.dto.PagoDto;
import org.deliverygo.ordersms.client.dto.ReembolsoDto;
import org.deliverygo.ordersms.client.dto.SolicitudPagoDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * INT-03 / INT-04 — Mock de Pagos (Eq. 4). Declarado como mock; lo reemplaza
 * {@code PagosClientHttp} con el perfil {@code integracion}.
 *
 * <p>Imita el comportamiento real: el alta de un pago responde PENDIENTE y la
 * autorizacion la resuelve Pagos en una llamada aparte que no hacemos nosotros. Un mock
 * que aprobara en el acto haria pasar los tests y fallar la demo.
 *
 * <p>Excepcion para poder ejercitar PAYMENT_REJECTED sin el modulo real: los importes de
 * {@value #LIMITE_APROBACION} en adelante nacen RECHAZADO. El reembolso siempre se
 * concede.
 */
@Component
@Profile("!integracion")
public class PagosClientMock implements PagosClient {

    /** Importe a partir del cual el mock rechaza el pago. */
    public static final String LIMITE_APROBACION = "100000.00";

    /** Estado con el que Pagos informa un reembolso concedido. */
    private static final String REEMBOLSO_APROBADO = "APROBADO";

    @Override
    public PagoDto iniciarPago(SolicitudPagoDto solicitud) {
        boolean rechazado = solicitud.monto().compareTo(new BigDecimal(LIMITE_APROBACION)) >= 0;
        return new PagoDto(
                UUID.randomUUID(),
                solicitud.codigoPedido(),
                rechazado ? EstadoPago.RECHAZADO : EstadoPago.PENDIENTE,
                solicitud.monto(),
                solicitud.moneda());
    }

    @Override
    public ReembolsoDto reembolsar(UUID codigoPago, UUID pedidoId, BigDecimal monto) {
        return new ReembolsoDto(
                UUID.randomUUID(),
                codigoPago,
                monto,
                REEMBOLSO_APROBADO,
                BigDecimal.ZERO);
    }
}
