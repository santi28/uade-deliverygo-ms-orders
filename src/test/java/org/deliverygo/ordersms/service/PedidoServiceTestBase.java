package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.client.CatalogoClient;
import org.deliverygo.ordersms.client.LogisticaClient;
import org.deliverygo.ordersms.client.PagosClient;
import org.deliverygo.ordersms.client.UsuariosClient;
import org.deliverygo.ordersms.exception.CodigoError;
import org.deliverygo.ordersms.exception.ReglaNegocioException;
import org.deliverygo.ordersms.mapper.PedidoMapper;
import org.deliverygo.ordersms.model.Pedido;
import org.deliverygo.ordersms.repository.HistorialEstadoRepository;
import org.deliverygo.ordersms.repository.PedidoRepository;
import org.deliverygo.ordersms.support.PedidosFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Armado comun de los tests de {@link PedidoService} (SRV-01..SRV-05).
 *
 * <p>Las dependencias que salen del modulo se mockean: los repositorios y los tres
 * clientes de integracion (INT-01 Catalogo, INT-02 Usuarios, INT-03/04 Pagos). En
 * cambio {@link MaquinaEstados} y {@link PedidoMapper} se usan de verdad: son logica
 * propia sin estado ni E/S, y mockearlos haria que los tests dejaran de verificar que el
 * CU respeta RN-04 y que DTO-02 sale armado.
 *
 * <p>El {@code PlatformTransactionManager} tambien se mockea: el service arma con el la
 * transaccion independiente de DM-07, y asi el callback corre sin necesidad de una base.
 */
@ExtendWith(MockitoExtension.class)
abstract class PedidoServiceTestBase {

    @Mock
    protected PedidoRepository pedidoRepository;

    @Mock
    protected HistorialEstadoRepository historialEstadoRepository;

    @Mock
    protected UsuariosClient usuariosClient;

    @Mock
    protected CatalogoClient catalogoClient;

    @Mock
    protected PagosClient pagosClient;

    @Mock
    protected LogisticaClient logisticaClient;

    @Mock
    protected PlatformTransactionManager transactionManager;

    protected final MaquinaEstados maquinaEstados = new MaquinaEstados();

    protected PedidoService service;

    @BeforeEach
    void prepararServicio() {
        service = new PedidoService(pedidoRepository, historialEstadoRepository, usuariosClient, catalogoClient,
                pagosClient, logisticaClient, maquinaEstados, new PedidoMapper(), transactionManager);
    }

    /**
     * Prende el interruptor del desvio del 17/11
     * ({@code deliverygo.pedidos.saltear-validacion-estado}), que apaga RN-04 en todos
     * los CU.
     *
     * <p>Es un campo inyectado por @Value, asi que en un test unitario se setea por
     * reflexion. Los casos que no lo llaman corren con el apagado, que es el valor por
     * defecto y el flujo aprobado en la planilla.
     */
    protected void dadoElInterruptorDeRn04Prendido() {
        ReflectionTestUtils.setField(service, "saltearValidacionEstado", true);
    }

    /** INT-02 — El cliente existe, esta activo y la direccion del pedido es suya. */
    protected void dadoClienteActivo() {
        given(usuariosClient.buscarUsuario(PedidosFixture.CLIENTE))
                .willReturn(Optional.of(PedidosFixture.usuarioActivo()));
    }

    /**
     * REP-04 — Emula la lectura del historial persistido devolviendo el del agregado.
     *
     * <p>El service arma DTO-02 con lo que devuelve el repositorio, no con
     * {@code pedido.getHistorial()}, asi que sin esto la respuesta saldria sin historial
     * y no se podria verificar que el CU dejo asentada su transicion.
     */
    protected void dadoHistorialPersistido(Pedido pedido) {
        given(historialEstadoRepository.findByPedidoIdOrderByFechaCambioAsc(PedidosFixture.PEDIDO_ID))
                .willAnswer(invocacion -> List.copyOf(pedido.getHistorial()));
    }

    /**
     * Codigo de la tabla de errores con el que corto el CU.
     *
     * <p>Lo que importa de una regla violada es con que codigo sale, porque de el depende
     * el HTTP de la respuesta ({@link CodigoError}); el mensaje se verifica solo cuando
     * distingue dos casos que comparten codigo.
     */
    protected static CodigoError codigoDe(Throwable error) {
        assertThat(error).isInstanceOf(ReglaNegocioException.class);
        return ((ReglaNegocioException) error).getCodigo();
    }
}
