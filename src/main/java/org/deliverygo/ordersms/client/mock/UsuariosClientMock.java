package org.deliverygo.ordersms.client.mock;

import org.deliverygo.ordersms.client.UsuariosClient;
import org.deliverygo.ordersms.client.dto.UsuarioDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * INT-02 — Mock de Usuarios (Eq. 1). Declarado como mock, sin fecha de reemplazo.
 *
 * <p>A diferencia de los otros mocks, este <strong>no</strong> se apaga con el perfil
 * {@code integracion}: no existe implementacion HTTP que lo reemplace. El modulo de
 * Usuarios del ecosistema solo publica {@code /auth/register}, {@code /auth/login} y
 * {@code /auth/password/forgot}; no hay {@code GET /api/v1/users/{id}} ni recurso de
 * direcciones del usuario, asi que RN-01 y RN-06 no se pueden validar contra nadie.
 *
 * <p>Decision del equipo (03/11): se confia en que el usuario viene validado por su
 * propio modulo. Desvio declarado en PLAN.md. Si Eq. 1 publica el endpoint, este mock
 * pasa a {@code @Profile("!integracion")} y aparece un {@code UsuariosClientHttp}.
 */
@Component
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
