package org.deliverygo.ordersms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO-01 — Cuerpo de API-01 {@code POST /api/v1/orders} (CU-01).
 *
 * <p>El cliente no viaja en el cuerpo: se toma de la identidad de quien llama
 * (RN-01). El pedido nace sin items; las lineas se cargan con CU-02.
 *
 * @param comercioId         comercio del pedido; fija el comercio unico de todas sus
 *                           lineas (RN-02)
 * @param direccionEntregaId direccion de entrega, validada contra Usuarios (RN-06, INT-02)
 */
@Schema(description = "Datos minimos para abrir un pedido en BORRADOR (DTO-01)")
public record CrearPedidoRequest(

        @Schema(description = "Comercio del pedido; todas sus lineas deben ser de este comercio (RN-02)",
                example = "99999999-9999-9999-9999-999999999999", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "comercioId es obligatorio")
        UUID comercioId,

        @Schema(description = "Direccion de entrega; tiene que ser una de las del cliente (RN-06)",
                example = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "direccionEntregaId es obligatoria")
        UUID direccionEntregaId) {
}
