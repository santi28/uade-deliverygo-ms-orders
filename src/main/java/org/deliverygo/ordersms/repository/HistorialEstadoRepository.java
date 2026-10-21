package org.deliverygo.ordersms.repository;

import org.deliverygo.ordersms.model.HistorialEstado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio del historial de estados (REP-04).
 *
 * <p>Las entradas se crean por cascada desde {@link org.deliverygo.ordersms.model.Pedido}
 * (REL-02, DM-06); este repositorio existe solo para leerlas sin cargar el agregado
 * completo.
 */
public interface HistorialEstadoRepository extends JpaRepository<HistorialEstado, UUID> {

    /**
     * REP-04 — Historial de un pedido en orden cronologico, apoyado en el indice
     * {@code (pedido_id, fecha_cambio)}.
     *
     * <p>CU-05 (consultar pedido y estado).
     */
    List<HistorialEstado> findByPedidoIdOrderByFechaCambioAsc(UUID pedidoId);
}
