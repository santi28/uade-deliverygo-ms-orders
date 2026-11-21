# Documentacion — Modulo Pedidos (Grupo 5)

Carpeta pedida por la catedra: aca va la documentacion de la primera entrega y el Swagger.

| Archivo | Que es |
| --- | --- |
| `openapi.yaml` | Contrato Swagger/OpenAPI 3.1 del modulo (API-01 a API-10), exportado de springdoc |
| `TPO_DeliveryGo_Primera_Entrega.xlsx` | Planilla de la primera entrega (diseño aprobado con correcciones) — **pendiente de subir** |

El diseño vigente y las etapas de desarrollo estan en [`../PLAN.md`](../PLAN.md).

## Swagger

- Archivo YAML: `doc/openapi.yaml`
- Swagger UI con la app levantada: http://localhost:8080/swagger-ui.html
- Regenerar el YAML despues de tocar la API:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
curl -s http://localhost:8080/v3/api-docs.yaml -o doc/openapi.yaml
```
