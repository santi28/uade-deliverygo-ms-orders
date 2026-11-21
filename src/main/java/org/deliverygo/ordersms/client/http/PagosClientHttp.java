package org.deliverygo.ordersms.client.http;

import org.deliverygo.ordersms.client.PagosClient;
import org.deliverygo.ordersms.client.dto.PagoDto;
import org.deliverygo.ordersms.client.dto.ReembolsoDto;
import org.deliverygo.ordersms.client.dto.SolicitudPagoDto;
import org.deliverygo.ordersms.client.dto.SolicitudReembolsoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * INT-03 / INT-04 / INT-05 — Cliente HTTP de Pagos (Eq. 4).
 *
 * <p>Reemplaza a {@code PagosClientMock} con el perfil {@code integracion}. INT-03
 * inicia el pago en CU-03, INT-04 lo reembolsa en CU-04 e INT-05 lo consulta en CU-09.
 * Contrato tomado del OpenAPI del ecosistema v1.4.0.
 *
 * <p>Las dos llamadas van con una clave de control derivada del pedido, que es el
 * mecanismo de idempotencia de Pagos: viaja en el cuerpo y no en una cabecera.
 * Reintentar la confirmacion no genera dos pagos, ni reintentar la cancelacion dos
 * reembolsos. La del reembolso lleva prefijo para no colisionar con la del pago del
 * mismo pedido.
 *
 * <p>Las dos exigen token: lo propaga el interceptor de {@code RestClientConfig} con el
 * {@code Authorization} de la peticion que estamos atendiendo.
 *
 * <p>Si Pagos no responde, la excepcion sale antes de tocar el estado del pedido: CU-03
 * deja el pedido en BORRADOR y CU-04 no lo cancela.
 */
@Component
@Profile("integracion")
public class PagosClientHttp extends ClienteHttpDeModulo implements PagosClient {

    static final String MODULO = "Pagos";

    /** INT-03 — Alta del pago del pedido. */
    static final String RUTA_PAGO = "/api/v1/payments";

    /** INT-04 — Reembolso de un pago. */
    static final String RUTA_REEMBOLSO = "/api/v1/payments/{codigoPago}/refund";

    /** INT-05 — Consulta de un pago. */
    static final String RUTA_CONSULTA = "/api/v1/payments/{codigoPago}";

    /** Prefijo de la clave de control del reembolso (INT-04). */
    static final String PREFIJO_REEMBOLSO = "reembolso-";

    /** Motivo que Pagos exige en el reembolso; en CU-04 siempre es el mismo. */
    static final String MOTIVO_REEMBOLSO = "Pedido cancelado por el cliente";

    private final UUID medioPagoPorDefecto;

    /**
     * @param medioPagoPorDefecto medio de pago ya registrado en Pagos que se usa cuando
     *                            la solicitud no trae codigo. Pagos exige un
     *                            {@code codigoMedioPago} (UUID) y nuestro
     *                            {@code ConfirmarPedidoRequest} recibe el medio como
     *                            texto, asi que hasta que se alinee ese DTO el codigo
     *                            sale de configuracion. Desvio declarado en PLAN.md
     */
    public PagosClientHttp(
            RestClient.Builder builder,
            @Value("${deliverygo.integraciones.pagos.base-url}") String baseUrl,
            @Value("${deliverygo.integraciones.pagos.medio-pago-por-defecto}") UUID medioPagoPorDefecto) {
        super(MODULO, baseUrl, builder);
        this.medioPagoPorDefecto = medioPagoPorDefecto;
    }

    @Override
    public PagoDto iniciarPago(SolicitudPagoDto solicitud) {
        SolicitudPagoDto aEnviar = solicitud.codigoMedioPago() != null
                ? solicitud
                : new SolicitudPagoDto(
                        solicitud.codigoPedido(),
                        solicitud.codigoUsuario(),
                        medioPagoPorDefecto,
                        solicitud.monto(),
                        solicitud.moneda(),
                        solicitud.claveControl());

        return invocar(() -> rest.post()
                .uri(RUTA_PAGO)
                .contentType(MediaType.APPLICATION_JSON)
                .body(aEnviar)
                .retrieve()
                .body(PagoDto.class));
    }

    @Override
    public ReembolsoDto reembolsar(UUID codigoPago, UUID pedidoId, BigDecimal monto) {
        SolicitudReembolsoDto solicitud = new SolicitudReembolsoDto(
                monto, MOTIVO_REEMBOLSO, PREFIJO_REEMBOLSO + pedidoId);

        return invocar(() -> rest.post()
                .uri(RUTA_REEMBOLSO, codigoPago)
                .contentType(MediaType.APPLICATION_JSON)
                .body(solicitud)
                .retrieve()
                .body(ReembolsoDto.class));
    }

    @Override
    public Optional<PagoDto> consultarPago(UUID codigoPago) {
        return buscar(() -> rest.get()
                .uri(RUTA_CONSULTA, codigoPago)
                .retrieve()
                .body(PagoDto.class));
    }
}
