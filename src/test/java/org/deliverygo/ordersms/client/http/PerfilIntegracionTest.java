package org.deliverygo.ordersms.client.http;

import org.deliverygo.ordersms.client.CatalogoClient;
import org.deliverygo.ordersms.client.PagosClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INT-01, INT-03 e INT-04 — Con el perfil {@code integracion} activo, los clientes
 * que se inyectan son los HTTP y no los mocks.
 *
 * <p>El cableado por perfil es facil de romper sin que nada avise: si un
 * {@code @Profile} quedara mal, la app arrancaria con los mocks y la demo mostraria
 * datos inventados como si fueran de los otros modulos. Este test es la red para eso.
 *
 * <p>No hace ninguna llamada HTTP: solo verifica que el contexto levante con el perfil
 * y de que tipo es cada bean. Las URL base salen de los valores por defecto de
 * {@code application-integracion.properties}.
 */
@SpringBootTest
@ActiveProfiles({"test", "integracion"})
class PerfilIntegracionTest {

    @Autowired
    private CatalogoClient catalogoClient;

    @Autowired
    private PagosClient pagosClient;

    /**
     * INT-02 sigue mockeado a proposito: el modulo de Usuarios no expone
     * {@code GET /api/v1/users/{id}} ni las direcciones del usuario, asi que no hay nada
     * que consumir. Declarado como desvio en PLAN.md.
     */
    @Autowired
    private org.deliverygo.ordersms.client.UsuariosClient usuariosClient;

    @Test
    @DisplayName("El perfil integracion reemplaza por HTTP los mocks de Catalogo y Pagos")
    void elPerfilIntegracionDejaFueraLosMocks() {
        assertThat(catalogoClient).isInstanceOf(CatalogoClientHttp.class);
        assertThat(pagosClient).isInstanceOf(PagosClientHttp.class);
        // INT-02 no tiene implementacion HTTP: sigue siendo el mock, declarado como tal.
        assertThat(usuariosClient)
                .isInstanceOf(org.deliverygo.ordersms.client.mock.UsuariosClientMock.class);
    }
}
