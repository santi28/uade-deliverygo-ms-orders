package org.deliverygo.ordersms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ENT-03 — Entrada inmutable del historial de estados de un {@link Pedido} (REL-02).
 *
 * <p>Se crea unicamente desde {@link Pedido#registrarTransicion(EstadoPedido, String)} y
 * no expone setters: una vez registrada, la transicion no se modifica.
 */
@Entity
@Table(name = "historial_estados", indexes = {
        // REP-04: historial de un pedido en orden cronologico.
        @Index(name = "idx_historial_pedido_fecha", columnList = "pedido_id, fecha_cambio")
})
public class HistorialEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, updatable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "estado_anterior", nullable = false, length = 20, updatable = false)
    private EstadoPedido estadoAnterior;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "estado_nuevo", nullable = false, length = 20, updatable = false)
    private EstadoPedido estadoNuevo;

    @Column(name = "fecha_cambio", nullable = false, updatable = false)
    private Instant fechaCambio;

    @Column(name = "motivo", length = 255, updatable = false)
    private String motivo;

    /** Requerido por JPA. */
    protected HistorialEstado() {
    }

    HistorialEstado(Pedido pedido, EstadoPedido estadoAnterior, EstadoPedido estadoNuevo, Instant fechaCambio,
            String motivo) {
        this.pedido = Objects.requireNonNull(pedido, "pedido es obligatorio");
        this.estadoAnterior = Objects.requireNonNull(estadoAnterior, "estadoAnterior es obligatorio");
        this.estadoNuevo = Objects.requireNonNull(estadoNuevo, "estadoNuevo es obligatorio");
        this.fechaCambio = Objects.requireNonNull(fechaCambio, "fechaCambio es obligatoria");
        this.motivo = motivo;
    }

    public UUID getId() {
        return id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public EstadoPedido getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoPedido getEstadoNuevo() {
        return estadoNuevo;
    }

    public Instant getFechaCambio() {
        return fechaCambio;
    }

    public String getMotivo() {
        return motivo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HistorialEstado otro)) {
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
        return "HistorialEstado{id=" + id
                + ", estadoAnterior=" + estadoAnterior
                + ", estadoNuevo=" + estadoNuevo
                + ", fechaCambio=" + fechaCambio
                + ", motivo='" + motivo + '\''
                + '}';
    }
}
