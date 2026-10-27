package org.deliverygo.ordersms.client.dto;

import java.util.List;
import java.util.UUID;

/**
 * INT-02 — Vista del usuario que devuelve Usuarios (Eq. 1).
 *
 * <p>Sostiene RN-01 (usuario registrado y activo) y RN-06 (la direccion de entrega tiene
 * que ser una de las suyas).
 *
 * @param direccionesIds direcciones registradas del usuario
 */
public record UsuarioDto(
        UUID id,
        String nombre,
        boolean activo,
        List<UUID> direccionesIds) {

    /** RN-06 — La direccion indicada pertenece al usuario. */
    public boolean tieneDireccion(UUID direccionId) {
        return direccionesIds != null && direccionesIds.contains(direccionId);
    }
}
