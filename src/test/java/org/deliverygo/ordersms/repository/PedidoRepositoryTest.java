package org.deliverygo.ordersms.repository;

import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de REP-01..03.
 */
@DataJpaTest
class PedidoRepositoryTest {

    private static final UUID CLIENTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTRO_CLIENTE = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID COMERCIO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DIRECCION = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TestEntityManager em;

    private Pedido persistir(UUID clienteId, EstadoPedido estado) {
        Pedido pedido = Pedido.crear(clienteId, COMERCIO, DIRECCION);
        if (estado != EstadoPedido.BORRADOR) {
            pedido.registrarTransicion(estado, "Setup del test");
        }
        return em.persistAndFlush(pedido);
    }

    @Test
    @DisplayName("REP-01: encuentra el borrador del cliente")
    void encuentraBorradorDelCliente() {
        Pedido borrador = persistir(CLIENTE, EstadoPedido.BORRADOR);
        em.clear();

        Optional<Pedido> encontrado = pedidoRepository.findByClienteIdAndEstado(CLIENTE, EstadoPedido.BORRADOR);

        assertThat(encontrado).map(Pedido::getId).contains(borrador.getId());
    }

    @Test
    @DisplayName("REP-01: no devuelve un pedido CONFIRMADO cuando se busca el borrador")
    void noDevuelvePedidoEnOtroEstado() {
        persistir(CLIENTE, EstadoPedido.CONFIRMADO);
        em.clear();

        assertThat(pedidoRepository.findByClienteIdAndEstado(CLIENTE, EstadoPedido.BORRADOR)).isEmpty();
    }

    @Test
    @DisplayName("REP-01: no devuelve el borrador de otro cliente")
    void noDevuelveBorradorDeOtroCliente() {
        persistir(OTRO_CLIENTE, EstadoPedido.BORRADOR);
        em.clear();

        assertThat(pedidoRepository.findByClienteIdAndEstado(CLIENTE, EstadoPedido.BORRADOR)).isEmpty();
    }

    @Test
    @DisplayName("REP-02: trae los items inicializados en la misma consulta (DM-02)")
    void traeItemsInicializados() {
        Pedido pedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);
        pedido.agregarItem(UUID.randomUUID(), "Milanesa napolitana", 2, new BigDecimal("1500.50"));
        pedido.agregarItem(UUID.randomUUID(), "Coca-Cola 1.5L", 3, new BigDecimal("1200.00"));
        UUID pedidoId = em.persistAndGetId(pedido, UUID.class);
        em.flush();
        em.clear();

        Pedido conItems = pedidoRepository.findByIdConItems(pedidoId).orElseThrow();

        // Verificado sobre el atributo: getItems() devuelve un wrapper unmodifiable, asi
        // que Hibernate.isInitialized() sobre el valor retornado daria siempre true.
        assertThat(estaCargada(conItems, "items")).isTrue();

        // Confirmacion por comportamiento: desvinculado del contexto de persistencia, la
        // coleccion sigue siendo legible porque ya vino cargada.
        em.getEntityManager().detach(conItems);
        assertThat(conItems.getItems()).hasSize(2);
        assertThat(conItems.getTotal()).isEqualByComparingTo(new BigDecimal("6601.00"));
    }

    @Test
    @DisplayName("REP-02: sin JOIN FETCH los items quedan perezosos")
    void findByIdDejaLosItemsPerezosos() {
        Pedido pedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);
        pedido.agregarItem(UUID.randomUUID(), "Empanada de carne", 6, new BigDecimal("800.00"));
        UUID pedidoId = em.persistAndGetId(pedido, UUID.class);
        em.flush();
        em.clear();

        Pedido sinFetch = pedidoRepository.findById(pedidoId).orElseThrow();

        assertThat(estaCargada(sinFetch, "items")).isFalse();
    }

    @Test
    @DisplayName("REP-03: devuelve el pedido cuando el cliente es el dueño")
    void devuelvePedidoDelDueno() {
        Pedido pedido = persistir(CLIENTE, EstadoPedido.BORRADOR);
        em.clear();

        assertThat(pedidoRepository.findByIdAndClienteId(pedido.getId(), CLIENTE))
                .map(Pedido::getId)
                .contains(pedido.getId());
    }

    @Test
    @DisplayName("REP-03: devuelve vacio si el pedido es de otro cliente")
    void noDevuelvePedidoDeOtroCliente() {
        Pedido pedido = persistir(CLIENTE, EstadoPedido.BORRADOR);
        em.clear();

        assertThat(pedidoRepository.findByIdAndClienteId(pedido.getId(), OTRO_CLIENTE)).isEmpty();
    }

    private boolean estaCargada(Object entidad, String atributo) {
        return em.getEntityManager()
                .getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(entidad, atributo);
    }
}
