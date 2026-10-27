package org.deliverygo.ordersms.repository;

import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.HistorialEstado;
import org.deliverygo.ordersms.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Tests de REP-04.
 */
@DataJpaTest
@ActiveProfiles("test")
class HistorialEstadoRepositoryTest {

    private static final UUID CLIENTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COMERCIO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DIRECCION = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private HistorialEstadoRepository historialEstadoRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("REP-04: devuelve las transiciones del pedido en orden cronologico")
    void devuelveHistorialEnOrdenCronologico() throws InterruptedException {
        Pedido pedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);
        pedido.registrarTransicion(EstadoPedido.CONFIRMADO, "Pago aprobado");
        // Separa los Instant para que el orden no dependa de un desempate arbitrario.
        Thread.sleep(5);
        pedido.registrarTransicion(EstadoPedido.EN_PREPARACION, "Aceptado por el comercio");
        Thread.sleep(5);
        pedido.registrarTransicion(EstadoPedido.CANCELADO, "Cancelado por el cliente");

        UUID pedidoId = em.persistAndGetId(pedido, UUID.class);
        em.flush();
        em.clear();

        List<HistorialEstado> historial = historialEstadoRepository.findByPedidoIdOrderByFechaCambioAsc(pedidoId);

        assertThat(historial).extracting(HistorialEstado::getEstadoAnterior, HistorialEstado::getEstadoNuevo)
                .containsExactly(
                        tuple(EstadoPedido.BORRADOR, EstadoPedido.CONFIRMADO),
                        tuple(EstadoPedido.CONFIRMADO, EstadoPedido.EN_PREPARACION),
                        tuple(EstadoPedido.EN_PREPARACION, EstadoPedido.CANCELADO));
        assertThat(historial).extracting(HistorialEstado::getFechaCambio).isSorted();
    }

    @Test
    @DisplayName("REP-04: no devuelve el historial de otro pedido")
    void noDevuelveHistorialDeOtroPedido() {
        Pedido pedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);
        pedido.registrarTransicion(EstadoPedido.CONFIRMADO, "Pago aprobado");
        em.persistAndFlush(pedido);

        Pedido otroPedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);
        UUID otroId = em.persistAndGetId(otroPedido, UUID.class);
        em.flush();
        em.clear();

        assertThat(historialEstadoRepository.findByPedidoIdOrderByFechaCambioAsc(otroId)).isEmpty();
    }
}
