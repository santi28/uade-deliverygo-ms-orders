package org.deliverygo.ordersms.client.dto;

import java.math.BigDecimal;

/**
 * INT-01 — Vista del producto que devuelve Catalogo (Eq. 2).
 *
 * <p>Es un DTO de transporte, nunca una entidad JPA: de aca salen el nombre y el precio
 * que {@code ItemPedido} congela (RN-03, DM-01).
 *
 * <p>Refleja el {@code ProductoResponseDTO} del OpenAPI del ecosistema (v1.4.0). De los
 * campos que publica Catalogo se toman solo los que necesitan las reglas: las
 * {@code categorias} se ignoran, y Jackson las descarta sin romper el mapeo.
 *
 * @param id         identificador de Catalogo; es numerico, no UUID (DM-03: el tipo lo
 *                   define el modulo que emite el ID)
 * @param comercioId comercio dueño del producto, tambien numerico (RN-02)
 * @param estado     estado de publicacion que informa Catalogo; junto con
 *                   {@code disponible} resuelve RN-05
 * @param disponible producto habilitado para la venta
 */
public record ProductoDto(
        Long id,
        Long comercioId,
        String nombre,
        BigDecimal precio,
        String estado,
        boolean disponible) {

    /** Estado de publicacion con el que Catalogo marca un producto vigente. */
    private static final String ESTADO_ACTIVO = "ACTIVO";

    /**
     * RN-05 — Producto que se puede vender: publicado y habilitado.
     *
     * <p>Catalogo informa las dos cosas por separado, asi que la conjuncion la hacemos
     * nosotros. {@code estado} nulo se trata como vigente: su 404 ya cubre el producto
     * inexistente o inactivo, de modo que un producto que llega con 200 esta publicado.
     */
    public boolean vendible() {
        return disponible && (estado == null || ESTADO_ACTIVO.equalsIgnoreCase(estado));
    }
}
