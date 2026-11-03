package org.deliverygo.ordersms;

import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.HistorialEstado;
import org.deliverygo.ordersms.model.Pedido;
import org.deliverygo.ordersms.repository.HistorialEstadoRepository;
import org.deliverygo.ordersms.repository.PedidoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida los datos de prueba del perfil {@code dev} ({@code db/seed-dev.sql}).
 *
 * <p>El script lo carga Spring al arrancar con Postgres, donde un error de nombre de
 * columna o de tipo recien se ve en la demo. Aca se ejecuta contra el H2 de los tests,
 * cuyo esquema sale de las mismas entidades, asi que una columna mal escrita rompe este
 * test y no el {@code docker compose up}.
 *
 * <p>Tambien verifica que sea idempotente: el script corre en cada arranque y la base del
 * compose es un volumen que sobrevive al {@code down}, asi que un INSERT sin guarda
 * duplicaria los pedidos de la demo o directamente no arrancaria.
 *
 * <p>Lo que el script siembra lo borra este test al terminar, para no dejar pedidos
 * sueltos a los demas.
 */
@SpringBootTest
@ActiveProfiles("test")
class DatosDePruebaDevTest {

    private static final UUID PEDIDO_CONFIRMADO = UUID.fromString("dddd0001-0000-4000-8000-000000000001");
    private static final UUID PEDIDO_CANCELADO = UUID.fromString("dddd0002-0000-4000-8000-000000000002");
    private static final UUID CLIENTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PAGO = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private HistorialEstadoRepository historialEstadoRepository;

    @BeforeEach
    @AfterEach
    void limpiarLaBase() {
        pedidoRepository.deleteAll();
    }

    private void cargarSeed() {
        ResourceDatabasePopulator seed = new ResourceDatabasePopulator(
                new ClassPathResource("db/seed-dev.sql"));
        seed.execute(dataSource);
    }

    @Test
    @DisplayName("el seed carga el pedido confirmado con sus lineas, su pago y su historial")
    void cargaElPedidoConfirmado() {
        cargarSeed();

        Pedido pedido = pedidoRepository.findByIdConItems(PEDIDO_CONFIRMADO).orElseThrow();
        assertThat(pedido.getClienteId()).isEqualTo(CLIENTE);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
        // Con pago asociado se puede demostrar CU-04 con reembolso (INT-04).
        assertThat(pedido.getPagoId()).isEqualTo(PAGO);
        // 2 x 8500 + 3 x 1200: el total sembrado coincide con la suma de los subtotales.
        assertThat(pedido.getTotal()).isEqualByComparingTo(new BigDecimal("20600.00"));
        assertThat(pedido.getItems())
                .extracting(item -> item.getSubtotal())
                .containsExactlyInAnyOrder(new BigDecimal("17000.00"), new BigDecimal("3600.00"));

        // REP-04: el historial llega en orden cronologico, como lo muestra CU-05.
        assertThat(historialEstadoRepository.findByPedidoIdOrderByFechaCambioAsc(PEDIDO_CONFIRMADO))
                .extracting(HistorialEstado::getEstadoNuevo)
                .containsExactly(EstadoPedido.BORRADOR, EstadoPedido.BORRADOR, EstadoPedido.CONFIRMADO);
    }

    @Test
    @DisplayName("el seed carga el pedido cancelado, que sirve para mostrar el 409 de RN-04")
    void cargaElPedidoCancelado() {
        cargarSeed();

        Pedido pedido = pedidoRepository.findByIdConItems(PEDIDO_CANCELADO).orElseThrow();
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(pedido.getPagoId()).isNull();
        assertThat(pedido.getItems()).hasSize(1);
        assertThat(pedido.getTotal()).isEqualByComparingTo(new BigDecimal("8500.00"));
    }

    @Test
    @DisplayName("no siembra ningun BORRADOR: CU-01 tiene que responder 201 en la demo")
    void noSiembraBorradores() {
        cargarSeed();

        // REP-01: un borrador abierto haria que el primer POST devolviera 200 y reutilizara.
        assertThat(pedidoRepository.findByClienteIdAndEstado(CLIENTE, EstadoPedido.BORRADOR)).isEmpty();
    }

    @Test
    @DisplayName("es idempotente: correrlo de nuevo no duplica nada")
    void esIdempotente() {
        cargarSeed();
        long pedidos = pedidoRepository.count();
        long historial = historialEstadoRepository.count();

        // Segundo arranque de la app sobre la misma base.
        cargarSeed();

        assertThat(pedidoRepository.count()).isEqualTo(pedidos);
        assertThat(historialEstadoRepository.count()).isEqualTo(historial);
        assertThat(pedidoRepository.findByIdConItems(PEDIDO_CONFIRMADO).orElseThrow().getItems()).hasSize(2);
    }
}
