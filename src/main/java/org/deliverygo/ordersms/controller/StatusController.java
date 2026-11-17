package org.deliverygo.ordersms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 * Health check del microservicio. Fuera de la planilla: no tiene ID de trazabilidad
 * (no es un CU, ni API-xx) y se agrega a pedido del equipo para que la demo, el
 * healthcheck de Docker y los otros módulos puedan comprobar que el servicio responde.
 *
 * <p>No toca la base ni las integraciones: responder 200 significa solo que el proceso
 * está levantado y sirviendo HTTP.
 */
@RestController
@Tag(name = "Status", description = "Health check del microservicio (fuera de la planilla)")
public class StatusController {

    /** {@code GET /status}: 200 con el texto {@code OK} si el servicio está arriba. */
    @GetMapping(value = "/status", produces = TEXT_PLAIN_VALUE)
    @Operation(summary = "Devuelve 200 con el texto OK si el microservicio está funcionando")
    @ApiResponse(responseCode = "200", description = "El servicio responde",
            content = @Content(mediaType = TEXT_PLAIN_VALUE,
                    examples = @ExampleObject(value = "OK")))
    public ResponseEntity<String> status() {
        return ResponseEntity.ok("OK");
    }
}
