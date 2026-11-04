package org.deliverygo.ordersms.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * INT-03 — Cuerpo de {@code POST /api/v1/payments} en CU-03
 * ({@code CrearPagoRequest} del OpenAPI del ecosistema v1.4.0).
 *
 * <p>Pagos exige los seis campos. {@code claveControl} es su mecanismo de
 * idempotencia: va en el cuerpo, no en una cabecera, asi que reintentar la confirmacion
 * del mismo pedido no genera dos pagos.
 *
 * @param codigoMedioPago medio de pago ya registrado en Pagos; lo elige el cliente, no
 *                        lo inventa Pedidos
 * @param claveControl    clave de idempotencia derivada del pedido
 */
public record SolicitudPagoDto(
        UUID codigoPedido,
        UUID codigoUsuario,
        UUID codigoMedioPago,
        BigDecimal monto,
        String moneda,
        String claveControl) {
}
