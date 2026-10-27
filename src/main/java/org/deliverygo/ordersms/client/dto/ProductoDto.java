package org.deliverygo.ordersms.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * INT-01 — Vista del producto que devuelve Catalogo (Eq. 2).
 *
 * <p>Es un DTO de transporte, nunca una entidad JPA: de aca salen el nombre y el precio
 * que {@code ItemPedido} congela (RN-03, DM-01).
 *
 * @param disponible producto activo y con stock (RN-05)
 */
public record ProductoDto(
        UUID id,
        UUID comercioId,
        String nombre,
        BigDecimal precio,
        boolean disponible) {
}
