package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.exception.EstadoInvalidoException;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.HistorialEstado;
import org.deliverygo.ordersms.model.Pedido;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * RN-04 — Transiciones validas de {@link EstadoPedido}.
 *
 * <p>Es el unico lugar donde se decide si un pedido puede pasar de un estado a otro. La
 * entidad {@link Pedido} sabe registrar la transicion pero no si es legal; el service
 * pide el cambio a traves de esta clase y nunca llama a
 * {@link Pedido#registrarTransicion(EstadoPedido, String)} por su cuenta.
 *
 * <p>Solo contiene las transiciones de los CU CORE. Las de Logistica
 * (CONFIRMADO - EN_PREPARACION - EN_CAMINO - ENTREGADO) se agregan cuando se encare esa
 * evolucion: hoy cualquier intento responde ORDER_INVALID_STATE.
 */
@Component
public class MaquinaEstados {

    /**
     * Destinos permitidos por estado de origen. Un estado que no figura como clave es
     * terminal: de CANCELADO no se sale.
     */
    private static final Map<EstadoPedido, Set<EstadoPedido>> TRANSICIONES = Map.of(
            // CU-02 modificar items (BORRADOR a BORRADOR), CU-03 confirmar, CU-04 cancelar.
            EstadoPedido.BORRADOR,
            EnumSet.of(EstadoPedido.BORRADOR, EstadoPedido.CONFIRMADO, EstadoPedido.CANCELADO),
            // CU-04 cancelar, solo si el reembolso responde REEMBOLSADO (INT-04).
            EstadoPedido.CONFIRMADO,
            EnumSet.of(EstadoPedido.CANCELADO));

    /** Indica si la transicion esta permitida, sin lanzar nada. */
    public boolean esValida(EstadoPedido desde, EstadoPedido hacia) {
        return TRANSICIONES.getOrDefault(desde, Set.of()).contains(hacia);
    }

    /**
     * Corta con ORDER_INVALID_STATE si la transicion no esta permitida.
     *
     * <p>Se usa para validar antes de tocar el agregado: asi un pedido que no se puede
     * modificar no queda a medio editar (cuando se lanza no se persiste nada).
     */
    public void validar(EstadoPedido desde, EstadoPedido hacia) {
        if (!esValida(desde, hacia)) {
            throw new EstadoInvalidoException(desde, hacia);
        }
    }

    /**
     * Valida la transicion y la aplica al pedido, dejando la entrada correspondiente en
     * el historial (REL-02) para que la lea CU-05.
     *
     * @param motivo queda asentado en el historial; conviene que diga que CU lo disparo
     * @return la entrada de historial registrada
     */
    public HistorialEstado transicionar(Pedido pedido, EstadoPedido nuevoEstado, String motivo) {
        validar(pedido.getEstado(), nuevoEstado);
        return pedido.registrarTransicion(nuevoEstado, motivo);
    }
}
