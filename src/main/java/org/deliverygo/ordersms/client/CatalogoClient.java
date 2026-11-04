package org.deliverygo.ordersms.client;

import org.deliverygo.ordersms.client.dto.ProductoDto;

import java.util.Optional;

/**
 * INT-01 — Cliente de Catalogo (Eq. 2), {@code GET /api/v1/productos/{id}}.
 *
 * <p>Usado por CU-01, CU-02 y CU-03 para resolver comercio (RN-02), disponibilidad
 * (RN-05) y precio (RN-03). Si el modulo no responde se lanza
 * {@code DependenciaNoDisponibleException}: nunca se asume un precio.
 */
public interface CatalogoClient {

    /**
     * Devuelve el producto, o vacio si Catalogo responde 404.
     *
     * <p>Para Catalogo el 404 cubre tanto el producto inexistente como el inactivo, asi
     * que vacio significa que el producto no se puede pedir, no solo que no existe.
     *
     * @param productoId id numerico de Catalogo (DM-03)
     */
    Optional<ProductoDto> buscarProducto(Long productoId);
}
