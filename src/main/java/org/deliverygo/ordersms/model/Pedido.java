package org.deliverygo.ordersms.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ENT-01 — Pedido: raiz del agregado.
 *
 * <p>Referencia a clientes, comercios, direcciones y pagos por ID plano (DM-03, DM-09):
 * no hay relaciones JPA hacia entidades de otros modulos. Los items (REL-01) y el
 * historial (REL-02) son propiedad exclusiva de esta raiz y se persisten por cascada.
 *
 * <p>Los metodos de esta clase solo garantizan la consistencia interna del agregado
 * (relacion bidireccional y total recalculado). Las reglas de negocio —validez de las
 * transiciones de estado (RN-04) entre otras— viven fuera de la entidad.
 */
@Entity
@Table(name = "pedidos", indexes = {
        // REP-01: pedidos de un cliente filtrados por estado.
        @Index(name = "idx_pedidos_cliente_estado", columnList = "cliente_id, estado")
})
public class Pedido {

    public static final String MONEDA_POR_DEFECTO = "ARS";

    private static final int ESCALA_MONETARIA = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    @Column(name = "comercio_id", nullable = false, updatable = false)
    private UUID comercioId;

    @Column(name = "direccion_entrega_id", nullable = false)
    private UUID direccionEntregaId;

    @Enumerated(EnumType.STRING)
    // Fuerza varchar: sin esto Hibernate 6 crea un tipo ENUM nativo en H2/MySQL.
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPedido estado = EstadoPedido.BORRADOR;

    @Column(name = "total", nullable = false, precision = 12, scale = ESCALA_MONETARIA)
    private BigDecimal total = BigDecimal.ZERO.setScale(ESCALA_MONETARIA);

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda = MONEDA_POR_DEFECTO;

    /** DM-09: referencia al pago; nula hasta que el pedido se asocia a uno. */
    @Column(name = "pago_id")
    private UUID pagoId;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** REL-01: lineas del pedido. */
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemPedido> items = new ArrayList<>();

    /** REL-02: historial de cambios de estado. */
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HistorialEstado> historial = new ArrayList<>();

    /** Requerido por JPA. */
    protected Pedido() {
    }

    private Pedido(UUID clienteId, UUID comercioId, UUID direccionEntregaId) {
        this.clienteId = Objects.requireNonNull(clienteId, "clienteId es obligatorio");
        this.comercioId = Objects.requireNonNull(comercioId, "comercioId es obligatorio");
        this.direccionEntregaId = Objects.requireNonNull(direccionEntregaId, "direccionEntregaId es obligatorio");
    }

    /**
     * Crea un pedido nuevo en estado {@link EstadoPedido#BORRADOR}, sin items y con total
     * cero en la moneda por defecto.
     */
    public static Pedido crear(UUID clienteId, UUID comercioId, UUID direccionEntregaId) {
        return new Pedido(clienteId, comercioId, direccionEntregaId);
    }

    /**
     * Agrega una linea al pedido congelando el precio unitario recibido (RN-03, DM-01),
     * mantiene la relacion bidireccional y recalcula el total.
     *
     * <p>No fusiona lineas del mismo producto: la unicidad (pedido, producto) la garantiza
     * la restriccion de {@link ItemPedido}. Usar {@link #buscarItem(UUID)} antes si lo que
     * se quiere es acumular cantidad.
     */
    public ItemPedido agregarItem(UUID productoId, String nombreProducto, int cantidad, BigDecimal precioUnitario) {
        ItemPedido item = new ItemPedido(this, productoId, nombreProducto, cantidad, precioUnitario);
        this.items.add(item);
        recalcularTotal();
        return item;
    }

    /**
     * Quita una linea del pedido rompiendo la relacion bidireccional (lo que dispara el
     * {@code orphanRemoval}) y recalcula el total.
     */
    public void quitarItem(ItemPedido item) {
        Objects.requireNonNull(item, "item es obligatorio");
        if (this.items.remove(item)) {
            item.desasociarPedido();
            recalcularTotal();
        }
    }

    public Optional<ItemPedido> buscarItem(UUID productoId) {
        return this.items.stream()
                .filter(item -> Objects.equals(item.getProductoId(), productoId))
                .findFirst();
    }

    /**
     * Recalcula el total como la suma de los subtotales de las lineas, con escala 2 y
     * redondeo HALF_UP.
     */
    public void recalcularTotal() {
        this.total = this.items.stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    /**
     * Registra el cambio de estado en el historial (REL-02) y lo aplica al pedido.
     *
     * <p>No valida si la transicion es legal: eso es responsabilidad de
     * {@code MaquinaEstados} (RN-04).
     */
    public HistorialEstado registrarTransicion(EstadoPedido nuevoEstado, String motivo) {
        Objects.requireNonNull(nuevoEstado, "nuevoEstado es obligatorio");
        HistorialEstado entrada = new HistorialEstado(this, this.estado, nuevoEstado, Instant.now(), motivo);
        this.historial.add(entrada);
        this.estado = nuevoEstado;
        return entrada;
    }

    /** DM-09: asocia el pedido a un pago ya registrado en el modulo de pagos. */
    public void asociarPago(UUID pagoId) {
        this.pagoId = Objects.requireNonNull(pagoId, "pagoId es obligatorio");
    }

    public void cambiarDireccionEntrega(UUID direccionEntregaId) {
        this.direccionEntregaId = Objects.requireNonNull(direccionEntregaId, "direccionEntregaId es obligatorio");
    }

    @PrePersist
    void alPersistir() {
        Instant ahora = Instant.now();
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
    }

    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public UUID getComercioId() {
        return comercioId;
    }

    public UUID getDireccionEntregaId() {
        return direccionEntregaId;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getMoneda() {
        return moneda;
    }

    public UUID getPagoId() {
        return pagoId;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    public Long getVersion() {
        return version;
    }

    public List<ItemPedido> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<HistorialEstado> getHistorial() {
        return Collections.unmodifiableList(historial);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pedido otro)) {
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
        return "Pedido{id=" + id
                + ", clienteId=" + clienteId
                + ", comercioId=" + comercioId
                + ", estado=" + estado
                + ", total=" + total
                + " " + moneda
                + '}';
    }
}
