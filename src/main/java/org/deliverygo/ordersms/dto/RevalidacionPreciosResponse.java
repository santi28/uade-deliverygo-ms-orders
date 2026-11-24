package org.deliverygo.ordersms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO-07 — Respuesta de API-07 {@code GET /api/v1/orders/{id}/price-check} (CU-07).
 *
 * <p>Compara el borrador con Catalogo sin modificarlo. Solo lista las lineas que ya no se
 * pueden cobrar como estan guardadas.
 *
 * @param valido        true si ninguna linea cambio: CU-03 pasaria la revalidacion
 * @param totalGuardado total del pedido con los precios congelados (RN-03)
 * @param totalVigente  total al precio de hoy de las lineas que se siguen vendiendo
 * @param diferencias   lineas con precio distinto o que ya no se venden
 */
@Schema(description = "Resultado de comparar el borrador con Catalogo (DTO-07)")
public record RevalidacionPreciosResponse(
        UUID pedidoId,
        boolean valido,
        BigDecimal totalGuardado,
        BigDecimal totalVigente,
        String moneda,
        List<DiferenciaLinea> diferencias) {

    /**
     * DTO-07 — Una linea que ya no coincide con Catalogo.
     *
     * @param precioVigente precio de hoy; nulo si el producto ya no esta en Catalogo
     * @param disponible    false si el producto ya no se puede vender en este pedido
     *                      (RN-05, RN-02)
     * @param motivo        codigo con el que CU-03 cortaria: PRICE_CHANGED,
     *                      PRODUCT_UNAVAILABLE o MIXED_MERCHANT
     */
    @Schema(description = "Linea que ya no coincide con Catalogo")
    public record DiferenciaLinea(
            Long productoId,
            BigDecimal precioGuardado,
            BigDecimal precioVigente,
            boolean disponible,
            @Schema(example = "PRICE_CHANGED") String motivo) {
    }
}
