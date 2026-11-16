package org.deliverygo.ordersms.service;

import org.deliverygo.ordersms.exception.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.*;
import java.util.List;
import static org.deliverygo.ordersms.support.PedidosFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** SRV-06 — limites, resumenes, orden y ausencia de efectos laterales. */
class ListarPedidosServiceTest extends PedidoServiceTestBase {
    @Test
    void paginaResumenesSinConsultarColeccionesNiDependencias() {
        var pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "fechaCreacion", "id"));
        when(pedidoRepository.findByClienteId(CLIENTE, pageable))
                .thenReturn(new PageImpl<>(List.of(borrador()), pageable, 3));
        var result = service.listarPedidos(CLIENTE, 0, 2);
        assertThat(result.pedidos()).hasSize(1);
        assertThat(result.pedidos().getFirst().id()).isEqualTo(PEDIDO_ID);
        assertThat(result.totalElementos()).isEqualTo(3);
        assertThat(result.totalPaginas()).isEqualTo(2);
        verify(pedidoRepository).findByClienteId(CLIENTE, pageable);
        verifyNoMoreInteractions(pedidoRepository);
        verifyNoInteractions(historialEstadoRepository, catalogoClient, pagosClient, usuariosClient);
    }
    @Test
    void paginaSinResultadosConservaMetadatos() {
        var pageable = PageRequest.of(5, 20, Sort.by(Sort.Direction.DESC, "fechaCreacion", "id"));
        when(pedidoRepository.findByClienteId(CLIENTE, pageable)).thenReturn(Page.empty(pageable));
        var result = service.listarPedidos(CLIENTE, 5, 20);
        assertThat(result.pedidos()).isEmpty();
        assertThat(result.pagina()).isEqualTo(5);
        assertThat(result.totalElementos()).isZero();
    }
    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,100", "1073741824,2"})
    void rechazaLimitesAntesDeConsultar(int pagina, int tamanio) {
        assertThatThrownBy(() -> service.listarPedidos(CLIENTE, pagina, tamanio))
                .isInstanceOf(ReglaNegocioException.class);
        verifyNoInteractions(pedidoRepository);
    }
    @Test
    void errorBaseNoSeConvierteEnListaVacia() {
        when(pedidoRepository.findByClienteId(eq(CLIENTE), any(Pageable.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB unavailable"));
        assertThatThrownBy(() -> service.listarPedidos(CLIENTE, 0, 20))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }
}
