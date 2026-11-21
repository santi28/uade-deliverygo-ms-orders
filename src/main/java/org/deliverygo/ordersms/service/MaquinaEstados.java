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
 * pide el cambio a traves de esta clase, siempre por SRV-08
 * ({@code PedidoService.registrarTransicion}), y nunca llama a
 * {@link Pedido#registrarTransicion(EstadoPedido, String)} por su cuenta.
 *
 * <p>Contiene las transiciones de los CU CORE y la de CU-10 (CONFIRMADO a
 * EN_PREPARACION). El resto del ciclo de entrega (EN_CAMINO, ENTREGADO) es de Logistica,
 * que no le avisa a Pedidos: cualquier intento responde ORDER_INVALID_STATE. Con la
 * entrega en curso el pedido tampoco se cancela desde aca.
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
            // CU-04 cancelar, solo si el reembolso responde REEMBOLSADO (INT-04); CU-10
            // Logistica registro la entrega (INT-06).
            EstadoPedido.CONFIRMADO,
            EnumSet.of(EstadoPedido.CANCELADO, EstadoPedido.EN_PREPARACION));

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

    /**
     * Aplica la transicion SIN validarla contra RN-04.
     *
     * <p>Desvio registrado en PLAN.md (17/11): existe solo para el interruptor
     * {@code deliverygo.pedidos.saltear-validacion-estado}, que apaga RN-04 en todos los
     * CU. Lo llama solo SRV-08, y solo con el interruptor prendido; apagado (el valor por
     * defecto) nadie lo llama y RN-04 sigue vigente.
     *
     * <p>No usarlo para nada mas: todo cambio de estado pasa por
     * {@link #transicionar(Pedido, EstadoPedido, String)}.
     *
     * @param motivo queda asentado en el historial; deja constancia de que se salteo RN-04
     * @return la entrada de historial registrada
     */
    public HistorialEstado transicionarSinValidar(Pedido pedido, EstadoPedido nuevoEstado, String motivo) {
        return pedido.registrarTransicion(nuevoEstado, motivo);
    }
}
