package org.deliverygo.ordersms.client;

import org.deliverygo.ordersms.client.dto.ProductoDto;

import java.util.Optional;
import java.util.UUID;

/**
 * INT-01 — Cliente de Catalogo (Eq. 2), {@code GET /api/v1/products/{id}}.
 *
 * <p>Usado por CU-01, CU-02 y CU-03 para resolver comercio (RN-02), disponibilidad
 * (RN-05) y precio (RN-03). Si el modulo no responde se lanza
 * {@code DependenciaNoDisponibleException}: nunca se asume un precio.
 */
public interface CatalogoClient {

    /** Devuelve el producto, o vacio si Catalogo responde 404. */
    Optional<ProductoDto> buscarProducto(UUID productoId);
}
