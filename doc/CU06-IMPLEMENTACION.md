# CU06: listado paginado de pedidos

Base: main e2cabf4857a5124221008555a06d534478f31a12. Implementación de CU06, sin modificaciones a CU09 ni Logística.

## API
GET /api/v1/orders?pagina=0&tamanio=20
Cabecera X-Cliente-Id: UUID, misma identidad provisional que el resto del proyecto.

Respuesta: pedidos (resúmenes con id, comercioId, estado, total, moneda, fechaCreacion), pagina, tamanio, totalElementos, totalPaginas.
Página base 0, tamaño 1 a 100. Desplazamiento (pagina × tamanio) limitado a Integer.MAX_VALUE por JPA; se valida con aritmética long antes de consultar. Orden fijo fechaCreacion DESC, id DESC para desempate. Sin resultados: 200 y colección vacía. Parámetros inválidos: 400. Fallos de BD siguen el manejo técnico existente, nunca se convierten en una lista vacía.

## Límite de seguridad
La consulta filtra por cliente, pero X-Cliente-Id es manipulable por quien llama. No constituye autenticación ni prueba de que los datos ajenos estén protegidos. RN-01 y la precondición de identidad verificada siguen pendientes de la integración de seguridad acordada; antes de exposición real hay que obtener el cliente de identidad validada. No se introdujo una autenticación paralela ni un endpoint inutilizable por requerir Principal que el proyecto no configura.

## Pruebas
Añadidas pruebas de servicio (paginación, metadatos, límites, errores BD, ausencia de escrituras/dependencias), controlador (parámetros/cabecera/respuesta) y repositorio (filtrado por cliente y páginas disjuntas).

Comando:
./mvnw -Dtest=ListarPedidosServiceTest,ListarPedidosControllerTest,PedidoRepositoryTest test

Verificación local en Mac (15/11/2026): suite completa Maven con Java 21, 185 tests, 0 fallos, 0 errores y 0 omitidos; BUILD SUCCESS. git diff --check sin errores. No se modificó pom.xml.

Integrar mediante revisión de cambios, sin reemplazar código que Santi haya añadido después del commit base. No reemplazar el proyecto entero si hay un webhook más nuevo.
