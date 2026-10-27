package org.deliverygo.ordersms.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.deliverygo.ordersms.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Traduce cualquier excepcion del modulo al formato normalizado {@link ErrorResponse}.
 *
 * <p>Es el unico lugar donde se decide el HTTP de cada error: los services lanzan
 * excepciones de negocio y no conocen la capa web.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Se propaga en todas las llamadas salientes (INT-01..INT-04). */
    public static final String HEADER_CORRELACION = "X-Correlation-Id";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Violacion de una regla de negocio; el codigo lo trae la excepcion. */
    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> manejarReglaNegocio(ReglaNegocioException ex, HttpServletRequest request) {
        return construir(ex.getCodigo(), ex.getMessage(), request);
    }

    /** ORDER_NOT_FOUND — pedido inexistente o de otro cliente. */
    @ExceptionHandler(PedidoNotFoundException.class)
    public ResponseEntity<ErrorResponse> manejarPedidoInexistente(PedidoNotFoundException ex,
            HttpServletRequest request) {
        return construir(CodigoError.ORDER_NOT_FOUND, ex.getMessage(), request);
    }

    /** ORDER_INVALID_STATE — transicion no permitida (RN-04). */
    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> manejarEstadoInvalido(EstadoInvalidoException ex,
            HttpServletRequest request) {
        return construir(CodigoError.ORDER_INVALID_STATE, ex.getMessage(), request);
    }

    /** DEPENDENCY_UNAVAILABLE — Catalogo, Usuarios o Pagos no respondio. */
    @ExceptionHandler(DependenciaNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> manejarDependenciaCaida(DependenciaNoDisponibleException ex,
            HttpServletRequest request) {
        log.warn("Dependencia no disponible: {}", ex.getMessage(), ex);
        return construir(CodigoError.DEPENDENCY_UNAVAILABLE, ex.getMessage(), request);
    }

    /** VALIDATION_ERROR — Bean Validation sobre el cuerpo de la peticion. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarCuerpoInvalido(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return construir(CodigoError.VALIDATION_ERROR, detalle, request);
    }

    /** VALIDATION_ERROR — falta una cabecera obligatoria (por ejemplo X-Cliente-Id). */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> manejarCabeceraFaltante(MissingRequestHeaderException ex,
            HttpServletRequest request) {
        return construir(CodigoError.VALIDATION_ERROR, "Falta la cabecera " + ex.getHeaderName(), request);
    }

    /** VALIDATION_ERROR — UUID mal formado en la ruta o en una cabecera. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarTipoInvalido(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return construir(CodigoError.VALIDATION_ERROR, "Valor invalido para " + ex.getName(), request);
    }

    /** VALIDATION_ERROR — JSON ilegible o ausente. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonIlegible(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return construir(CodigoError.VALIDATION_ERROR, "El cuerpo de la peticion no es un JSON valido", request);
    }

    /**
     * Red de contencion: ningun fallo se escapa sin {@link ErrorResponse}.
     *
     * <p>Antes de dar 500 reconoce los errores de transporte que Spring MVC ya resolvio con
     * un status propio —ruta inexistente, metodo no soportado, media type invalido—, que
     * implementan {@code org.springframework.web.ErrorResponse}. Se respeta ese status y se
     * usa su nombre HTTP como {@code code}: no son errores de negocio, asi que no llevan un
     * codigo de la tabla del plan.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarInesperado(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse errorDeSpring) {
            HttpStatus status = HttpStatus.valueOf(errorDeSpring.getStatusCode().value());
            return construir(status, status.name(), status.getReasonPhrase(), request);
        }
        log.error("Error inesperado procesando {}", request.getRequestURI(), ex);
        return construir(CodigoError.INTERNAL_ERROR, "Error interno del modulo de pedidos", request);
    }

    private ResponseEntity<ErrorResponse> construir(CodigoError codigo, String mensaje, HttpServletRequest request) {
        return construir(codigo.getHttpStatus(), codigo.name(), mensaje, request);
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String codigo, String mensaje,
            HttpServletRequest request) {
        ErrorResponse cuerpo = new ErrorResponse(
                Instant.now(),
                status.value(),
                codigo,
                mensaje,
                request.getRequestURI(),
                correlationId(request));
        return ResponseEntity.status(status).body(cuerpo);
    }

    /** Toma el {@code X-Correlation-Id} entrante o genera uno para poder rastrear el caso. */
    private String correlationId(HttpServletRequest request) {
        String correlationId = request.getHeader(HEADER_CORRELACION);
        return (correlationId == null || correlationId.isBlank()) ? UUID.randomUUID().toString() : correlationId;
    }
}
