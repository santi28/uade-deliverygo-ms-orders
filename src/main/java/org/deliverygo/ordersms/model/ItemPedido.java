package org.deliverygo.ordersms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * ENT-02 — Linea de un {@link Pedido}.
 *
 * <p>Referencia al producto por ID plano (DM-03) y guarda un snapshot de su nombre y de
 * su precio unitario al momento de agregarlo (RN-03, DM-01): cambios posteriores en el
 * catalogo no alteran pedidos ya armados. El nombre del snapshot alimenta DTO-02.
 */
@Entity
@Table(name = "items_pedido", uniqueConstraints = {
        @UniqueConstraint(name = "uk_items_pedido_pedido_producto", columnNames = { "pedido_id", "producto_id" })
})
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(name = "producto_id", nullable = false, updatable = false)
    private UUID productoId;

    /** Snapshot del nombre del producto (DTO-02). */
    @Column(name = "nombre_producto", nullable = false)
    private String nombreProducto;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    /** Precio congelado al agregar la linea (RN-03, DM-01). */
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal precioUnitario;

    /** Requerido por JPA. */
    protected ItemPedido() {
    }

    ItemPedido(Pedido pedido, UUID productoId, String nombreProducto, int cantidad, BigDecimal precioUnitario) {
        this.pedido = Objects.requireNonNull(pedido, "pedido es obligatorio");
        this.productoId = Objects.requireNonNull(productoId, "productoId es obligatorio");
        this.nombreProducto = Objects.requireNonNull(nombreProducto, "nombreProducto es obligatorio");
        this.precioUnitario = Objects.requireNonNull(precioUnitario, "precioUnitario es obligatorio");
        this.cantidad = validarCantidad(cantidad);
    }

    private static int validarCantidad(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
        }
        return cantidad;
    }

    /**
     * Cambia la cantidad de la linea. Quien la invoque debe recalcular el total del pedido
     * con {@link Pedido#recalcularTotal()}.
     */
    public void cambiarCantidad(int cantidad) {
        this.cantidad = validarCantidad(cantidad);
    }

    /** Subtotal calculado, no persistido: {@code precioUnitario * cantidad}. */
    @Transient
    public BigDecimal getSubtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    /** Rompe la relacion bidireccional; solo lo usa {@link Pedido#quitarItem(ItemPedido)}. */
    void desasociarPedido() {
        this.pedido = null;
    }

    public UUID getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public UUID getProductoId() {
        return productoId;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemPedido otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "ItemPedido{id=" + id
                + ", productoId=" + productoId
                + ", nombreProducto='" + nombreProducto + '\''
                + ", cantidad=" + cantidad
                + ", precioUnitario=" + precioUnitario
                + '}';
    }
}
