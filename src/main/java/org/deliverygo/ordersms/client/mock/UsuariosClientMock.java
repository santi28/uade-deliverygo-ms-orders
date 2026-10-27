package org.deliverygo.ordersms.client.mock;

import org.deliverygo.ordersms.client.UsuariosClient;
import org.deliverygo.ordersms.client.dto.UsuarioDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * INT-02 — Mock de Usuarios (Eq. 1). Declarado como mock hasta la Etapa 4.
 *
 * <p>Activo salvo con el perfil {@code integracion}, donde toma su lugar la
 * implementacion HTTP real.
 */
@Component
@Profile("!integracion")
public class UsuariosClientMock implements UsuariosClient {

    private static final Map<UUID, UsuarioDto> USUARIOS = Map.of(
            DatosMock.CLIENTE_ACTIVO,
            new UsuarioDto(DatosMock.CLIENTE_ACTIVO, "Cliente de prueba", true,
                    List.of(DatosMock.DIRECCION_CLIENTE_ACTIVO)),
            DatosMock.CLIENTE_INACTIVO,
            new UsuarioDto(DatosMock.CLIENTE_INACTIVO, "Cliente dado de baja", false,
                    List.of(DatosMock.DIRECCION_CLIENTE_INACTIVO)));

    @Override
    public Optional<UsuarioDto> buscarUsuario(UUID usuarioId) {
        return Optional.ofNullable(USUARIOS.get(usuarioId));
    }
}
