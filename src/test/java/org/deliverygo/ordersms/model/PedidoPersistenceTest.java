package org.deliverygo.ordersms.model;

import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de persistencia del agregado Pedido (ENT-01..03, REL-01, REL-02).
 */
@DataJpaTest
@ActiveProfiles("test")
class PedidoPersistenceTest {

    private static final UUID CLIENTE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COMERCIO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DIRECCION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private TestEntityManager em;

    private static Pedido nuevoPedido() {
        return Pedido.crear(CLIENTE_ID, COMERCIO_ID, DIRECCION_ID);
    }

    private long contarItemsDe(UUID pedidoId) {
        Number cantidad = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM items_pedido WHERE pedido_id = ?1")
                .setParameter(1, pedidoId)
                .getSingleResult();
        return cantidad.longValue();
    }

    @Test
    @DisplayName("REL-01: un pedido con dos items persiste por cascade y el total es correcto")
    void persisteItemsPorCascadeYCalculaTotal() {
        Pedido pedido = nuevoPedido();
        pedido.agregarItem(UUID.randomUUID(), "Milanesa napolitana", 2, new BigDecimal("1500.50"));
        pedido.agregarItem(UUID.randomUUID(), "Coca-Cola 1.5L", 3, new BigDecimal("1200.00"));

        UUID pedidoId = em.persistAndGetId(pedido, UUID.class);
        em.flush();
        em.clear();

        Pedido recuperado = em.find(Pedido.class, pedidoId);
        assertThat(recuperado.getItems()).hasSize(2);
        // 2 * 1500.50 + 3 * 1200.00 = 6601.00
        assertThat(recuperado.getTotal()).isEqualByComparingTo(new BigDecimal("6601.00"));
        assertThat(recuperado.getTotal().scale()).isEqualTo(2);
        assertThat(recuperado.getMoneda()).isEqualTo("ARS");
        assertThat(recuperado.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(recuperado.getPagoId()).isNull();
        assertThat(recuperado.getFechaCreacion()).isNotNull();
        assertThat(recuperado.getFechaActualizacion()).isNotNull();
    }

    @Test
    @DisplayName("REL-01: quitarItem + flush borra la fila (orphanRemoval)")
    void quitarItemBorraLaFila() {
        Pedido pedido = nuevoPedido();
        pedido.agregarItem(UUID.randomUUID(), "Empanada de carne", 6, new BigDecimal("800.00"));
        ItemPedido aQuitar = pedido.agregarItem(UUID.randomUUID(), "Flan", 1, new BigDecimal("2000.00"));

        UUID pedidoId = em.persistAndGetId(pedido, UUID.class);
        em.flush();
        assertThat(contarItemsDe(pedidoId)).isEqualTo(2);

        pedido.quitarItem(aQuitar);
        em.flush();

        assertThat(contarItemsDe(pedidoId)).isEqualTo(1);
        assertThat(pedido.getTotal()).isEqualByComparingTo(new BigDecimal("4800.00"));
    }

    @Test
    @DisplayName("REL-02: registrarTransicion guarda el historial con el estado anterior")
    void registrarTransicionGuardaHistorial() {
        Pedido pedido = nuevoPedido();
        pedido.registrarTransicion(EstadoPedido.CONFIRMADO, "Confirmado por el cliente");

        UUID pedidoId = em.persistAndGetId(pedido, UUID.class);
        em.flush();
        em.clear();

        Pedido recuperado = em.find(Pedido.class, pedidoId);
        assertThat(recuperado.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);

        List<HistorialEstado> historial = recuperado.getHistorial();
        assertThat(historial).hasSize(1);
        HistorialEstado entrada = historial.get(0);
        assertThat(entrada.getEstadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(entrada.getEstadoNuevo()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(entrada.getMotivo()).isEqualTo("Confirmado por el cliente");
        assertThat(entrada.getFechaCambio()).isNotNull();
    }

    @Test
    @DisplayName("DM-04: el estado se persiste como texto")
    void estadoSePersisteComoTexto() {
        Pedido pedido = nuevoPedido();
        pedido.registrarTransicion(EstadoPedido.EN_CAMINO, null);

        UUID pedidoId = em.persistAndGetId(pedido, UUID.class);
        em.flush();

        Object estadoPedido = em.getEntityManager()
                .createNativeQuery("SELECT estado FROM pedidos WHERE id = ?1")
                .setParameter(1, pedidoId)
                .getSingleResult();
        assertThat(estadoPedido).isEqualTo("EN_CAMINO");

        Object estadoAnterior = em.getEntityManager()
                .createNativeQuery("SELECT estado_anterior FROM historial_estados WHERE pedido_id = ?1")
                .setParameter(1, pedidoId)
                .getSingleResult();
        assertThat(estadoAnterior).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("ENT-02: el mismo producto dos veces en un pedido viola la unique")
    void productoDuplicadoVialaLaUnique() {
        UUID productoId = UUID.randomUUID();
        Pedido pedido = nuevoPedido();
        pedido.agregarItem(productoId, "Pizza muzzarella", 1, new BigDecimal("9000.00"));
        pedido.agregarItem(productoId, "Pizza muzzarella", 2, new BigDecimal("9000.00"));

        em.persist(pedido);

        assertThatThrownBy(() -> em.flush())
                .isInstanceOfAny(PersistenceException.class, DataIntegrityViolationException.class);
    }
}
