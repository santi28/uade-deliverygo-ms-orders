package org.deliverygo.ordersms.client.mock;

import org.deliverygo.ordersms.client.PagosClient;
import org.deliverygo.ordersms.client.dto.EstadoPago;
import org.deliverygo.ordersms.client.dto.PagoDto;
import org.deliverygo.ordersms.client.dto.ReembolsoDto;
import org.deliverygo.ordersms.client.dto.SolicitudPagoDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * INT-03 / INT-04 / INT-05 — Mock de Pagos (Eq. 4). Declarado como mock; lo reemplaza
 * {@code PagosClientHttp} con el perfil {@code integracion}.
 *
 * <p>Imita el comportamiento real: el alta de un pago responde PENDIENTE y la
 * autorizacion la resuelve Pagos en una llamada aparte que no hacemos nosotros. Un mock
 * que aprobara en el acto haria pasar los tests y fallar la demo.
 *
 * <p>Excepcion para poder ejercitar PAYMENT_REJECTED sin el modulo real: los importes de
 * {@value #LIMITE_APROBACION} en adelante nacen RECHAZADO. El reembolso siempre se
 * concede.
 *
 * <p>Para CU-09 recuerda en memoria los pagos que inicio. Al consultarlos, los que
 * nacieron PENDIENTE vuelven APROBADO, como si Pagos ya hubiera corrido su
 * {@code /approve}; los rechazados siguen RECHAZADO. Un pago que no inicio este proceso
 * (por ejemplo, despues de reiniciar la app) no existe, igual que un 404 de Pagos.
 */
@Component
@Profile("!integracion")
public class PagosClientMock implements PagosClient {

    /** Importe a partir del cual el mock rechaza el pago. */
    public static final String LIMITE_APROBACION = "100000.00";

    /** Estado con el que Pagos informa un reembolso concedido. */
    private static final String REEMBOLSO_APROBADO = "APROBADO";

    /** Pagos iniciados por este proceso, por codigo de pago (INT-05). */
    private final Map<UUID, PagoDto> pagosIniciados = new ConcurrentHashMap<>();

    @Override
    public PagoDto iniciarPago(SolicitudPagoDto solicitud) {
        boolean rechazado = solicitud.monto().compareTo(new BigDecimal(LIMITE_APROBACION)) >= 0;
        PagoDto pago = new PagoDto(
                UUID.randomUUID(),
                solicitud.codigoPedido(),
                rechazado ? EstadoPago.RECHAZADO : EstadoPago.PENDIENTE,
                solicitud.monto(),
                solicitud.moneda());
        pagosIniciados.put(pago.codigoPago(), pago);
        return pago;
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

    @Override
    public Optional<PagoDto> consultarPago(UUID codigoPago) {
        return Optional.ofNullable(pagosIniciados.computeIfPresent(codigoPago, (codigo, pago) ->
                pago.estado() == EstadoPago.PENDIENTE
                        ? new PagoDto(codigo, pago.codigoPedido(), EstadoPago.APROBADO, pago.monto(), pago.moneda())
                        : pago));
    }
}
