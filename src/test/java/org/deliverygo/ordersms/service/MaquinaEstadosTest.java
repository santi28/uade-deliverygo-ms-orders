package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.exception.EstadoInvalidoException;
import org.deliverygo.ordersms.model.EstadoPedido;
import org.deliverygo.ordersms.model.HistorialEstado;
import org.deliverygo.ordersms.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de {@link MaquinaEstados} (RN-04).
 *
 * <p>Cubre la tabla de transiciones de PLAN.md: lo permitido, lo prohibido y que la
 * transicion aplicada quede asentada en el historial (REL-02).
 */
class MaquinaEstadosTest {

    private static final UUID CLIENTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COMERCIO = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID DIRECCION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final MaquinaEstados maquinaEstados = new MaquinaEstados();

    @ParameterizedTest
    @CsvSource({
            // CU-02 modificar items.
            "BORRADOR, BORRADOR",
            // CU-03 confirmar con pago APROBADO.
            "BORRADOR, CONFIRMADO",
            // CU-04 cancelar sin reembolso.
            "BORRADOR, CANCELADO",
            // CU-04 cancelar con reembolso REEMBOLSADO.
            "CONFIRMADO, CANCELADO"
    })
    @DisplayName("acepta las transiciones de los CU CORE")
    void aceptaTransicionesDeLosCuCore(EstadoPedido desde, EstadoPedido hacia) {
        assertThat(maquinaEstados.esValida(desde, hacia)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            // Un pedido confirmado no vuelve a borrador: CU-02 no lo puede editar.
            "CONFIRMADO, BORRADOR",
            // Confirmar dos veces.
            "CONFIRMADO, CONFIRMADO",
            // Reabrir o reconfirmar un pedido cancelado.
            "CANCELADO, BORRADOR",
            "CANCELADO, CONFIRMADO",
            "CANCELADO, CANCELADO",
            // Logistica queda fuera de los CU CORE hasta que se encare esa evolucion.
            "CONFIRMADO, EN_PREPARACION",
            "CONFIRMADO, EN_CAMINO",
            "CONFIRMADO, ENTREGADO",
            "BORRADOR, ENTREGADO"
    })
    @DisplayName("rechaza las transiciones fuera de la tabla")
    void rechazaTransicionesFueraDeLaTabla(EstadoPedido desde, EstadoPedido hacia) {
        assertThat(maquinaEstados.esValida(desde, hacia)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(EstadoPedido.class)
    @DisplayName("CANCELADO es terminal: no sale hacia ningun estado")
    void canceladoEsTerminal(EstadoPedido destino) {
        assertThat(maquinaEstados.esValida(EstadoPedido.CANCELADO, destino)).isFalse();
    }

    @Test
    @DisplayName("validar no lanza nada cuando la transicion esta permitida")
    void validarAceptaTransicionPermitida() {
        maquinaEstados.validar(EstadoPedido.BORRADOR, EstadoPedido.CONFIRMADO);
    }

    @Test
    @DisplayName("validar corta con ORDER_INVALID_STATE cuando no esta permitida")
    void validarRechazaTransicionProhibida() {
        assertThatThrownBy(() -> maquinaEstados.validar(EstadoPedido.CONFIRMADO, EstadoPedido.BORRADOR))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("CONFIRMADO")
                .hasMessageContaining("BORRADOR");
    }

    @Test
    @DisplayName("transicionar aplica el estado y lo registra en el historial")
    void transicionarRegistraEnElHistorial() {
        Pedido pedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);

        HistorialEstado entrada = maquinaEstados.transicionar(pedido, EstadoPedido.CONFIRMADO, "CU-03: pago APROBADO");

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(entrada.getEstadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(entrada.getEstadoNuevo()).isEqualTo(EstadoPedido.CONFIRMADO);
        assertThat(entrada.getMotivo()).isEqualTo("CU-03: pago APROBADO");
        assertThat(pedido.getHistorial()).containsExactly(entrada);
    }

    @Test
    @DisplayName("la edicion de un borrador queda asentada como BORRADOR a BORRADOR")
    void transicionarRegistraLaEdicionDelBorrador() {
        Pedido pedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);

        maquinaEstados.transicionar(pedido, EstadoPedido.BORRADOR, "CU-02: 1 agregados, 0 modificados, 0 quitados");

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.BORRADOR);
        assertThat(pedido.getHistorial()).hasSize(1);
        assertThat(pedido.getHistorial().getFirst().getEstadoAnterior()).isEqualTo(EstadoPedido.BORRADOR);
    }

    @Test
    @DisplayName("una transicion prohibida no toca el pedido")
    void transicionarProhibidaNoTocaElPedido() {
        Pedido pedido = Pedido.crear(CLIENTE, COMERCIO, DIRECCION);
        maquinaEstados.transicionar(pedido, EstadoPedido.CANCELADO, "CU-04: cancelado por el cliente");

        assertThatThrownBy(() -> maquinaEstados.transicionar(pedido, EstadoPedido.CONFIRMADO, "no deberia pasar"))
                .isInstanceOf(EstadoInvalidoException.class);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(pedido.getHistorial()).hasSize(1);
    }
}
