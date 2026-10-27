package org.deliverygo.ordersms.client;

import org.deliverygo.ordersms.client.dto.UsuarioDto;

import java.util.Optional;
import java.util.UUID;

/**
 * INT-02 — Cliente de Usuarios (Eq. 1), {@code GET /api/v1/users/{id}} con sus
 * direcciones.
 *
 * <p>Usado por CU-01 y CU-03 para RN-01 y RN-06. Si el modulo no responde se lanza
 * {@code DependenciaNoDisponibleException} y el pedido no se crea ni se confirma.
 */
public interface UsuariosClient {

    /** Devuelve el usuario con sus direcciones, o vacio si Usuarios responde 404. */
    Optional<UsuarioDto> buscarUsuario(UUID usuarioId);
}
