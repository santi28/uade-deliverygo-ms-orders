package org.deliverygo.ordersms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Formato normalizado de error del modulo; lo arma {@code GlobalExceptionHandler} y es
 * la unica forma en que el modulo reporta fallos.
 *
 * @param timestamp     momento en que se genero la respuesta
 * @param status        codigo HTTP
 * @param code          codigo de negocio ({@code CodigoError})
 * @param message       detalle legible, sin datos internos
 * @param path          URI de la peticion
 * @param correlationId valor de {@code X-Correlation-Id}, o uno generado si no vino
 */
@Schema(description = "Formato normalizado de error del modulo Pedidos")
public record ErrorResponse(

        @Schema(example = "2025-10-27T21:57:49.211Z")
        Instant timestamp,

        @Schema(example = "422")
        int status,

        @Schema(description = "Codigo de negocio estable: VALIDATION_ERROR, ORDER_NOT_FOUND, "
                + "ORDER_INVALID_STATE, MIXED_MERCHANT, PRODUCT_UNAVAILABLE, PRICE_CHANGED, "
                + "PAYMENT_REJECTED o DEPENDENCY_UNAVAILABLE", example = "MIXED_MERCHANT")
        String code,

        @Schema(example = "El cliente ya tiene un borrador abierto de otro comercio")
        String message,

        @Schema(example = "/api/v1/orders")
        String path,

        @Schema(description = "X-Correlation-Id de la peticion, o uno generado si no vino",
                example = "0affd3bd-9ef5-4fe2-9057-2f74cd3b179d")
        String correlationId) {
}
