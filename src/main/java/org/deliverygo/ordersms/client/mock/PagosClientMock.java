package org.deliverygo.ordersms.client.mock;

import org.deliverygo.ordersms.client.PagosClient;
import org.deliverygo.ordersms.client.dto.EstadoPago;
import org.deliverygo.ordersms.client.dto.PagoDto;
import org.deliverygo.ordersms.client.dto.SolicitudPagoDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * INT-03 / INT-04 — Mock de Pagos (Eq. 4). Declarado como mock hasta la Etapa 4.
 *
 * <p>Regla del mock: aprueba todo salvo los importes de {@value #LIMITE_APROBACION} en
 * adelante, para poder ejercitar PAYMENT_REJECTED desde Swagger. El reembolso siempre
 * responde REEMBOLSADO.
 */
@Component
@Profile("!integracion")
public class PagosClientMock implements PagosClient {

    /** Importe a partir del cual el mock rechaza el cobro. */
    public static final String LIMITE_APROBACION = "100000.00";

    @Override
    public PagoDto cobrar(SolicitudPagoDto solicitud) {
        boolean rechazado = solicitud.amount().compareTo(new BigDecimal(LIMITE_APROBACION)) >= 0;
        return new PagoDto(
                UUID.randomUUID(),
                rechazado ? EstadoPago.RECHAZADO : EstadoPago.APROBADO,
                solicitud.amount());
    }

    @Override
    public PagoDto reembolsar(UUID pagoId, UUID pedidoId) {
        return new PagoDto(pagoId, EstadoPago.REEMBOLSADO, null);
    }
}
