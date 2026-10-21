package org.deliverygo.ordersms.repository;

import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio de la raiz del agregado {@link Pedido} (REP-01..03).
 *
 * <p>Los items se persisten y se borran por cascada desde {@link Pedido} (DM-06): no
 * existe {@code ItemPedidoRepository} ni REP-05.
 */
public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

    /**
     * REP-01 — Busca el pedido de un cliente en un estado dado; se usa para recuperar el
     * borrador activo del cliente.
     *
     * <p>CU-01 (crear pedido o borrador).
     */
    Optional<Pedido> findByClienteIdAndEstado(UUID clienteId, EstadoPedido estado);

    /**
     * REP-02 — Pedido con sus items ya inicializados en una sola consulta (DM-02),
     * evitando el N+1 al recalcular el total.
     *
     * <p>Trae unicamente {@code items}: sumar {@code historial} al mismo
     * {@code JOIN FETCH} provoca {@code MultipleBagFetchException}. El historial se lee
     * aparte con REP-04.
     *
     * <p>CU-02 (gestionar items), CU-03 (confirmar pedido).
     */
    @Query("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.items WHERE p.id = :id")
    Optional<Pedido> findByIdConItems(@Param("id") UUID id);

    /**
     * REP-03 — Pedido por id verificando que pertenezca al cliente indicado; si no es su
     * dueño devuelve vacio y la capa de servicio responde ORDER_NOT_FOUND.
     *
     * <p>CU-04 (cancelar pedido), CU-05 (consultar pedido y estado).
     */
    Optional<Pedido> findByIdAndClienteId(UUID id, UUID clienteId);
}
