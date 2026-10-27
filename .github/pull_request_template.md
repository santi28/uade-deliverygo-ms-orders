## Que hace

<!-- Una o dos lineas. Si cierra una issue del plan: Closes #<n> -->

## Trazabilidad

<!-- IDs tocados: CU, RN, ENT, REP, SRV, API, DTO, INT, DM. Ej: CU-03, SRV-03, API-03, RN-04 -->

- Etapa: <!-- etapa-0 .. etapa-5 -->
- IDs:

## Como probarlo

<!-- Pasos desde Swagger o curl, con el caso feliz y al menos un error -->

1.

## Checklist (Definition of Done)

- [ ] El endpoint coincide con el API/DTO de PLAN.md y se probo desde Swagger
- [ ] Las reglas de negocio estan en el Service, no en el Controller
- [ ] Validacion, inexistente y estado invalido devuelven `ErrorResponse`
- [ ] Tests del camino feliz y de cada flujo alternativo, en verde (`./mvnw test`)
- [ ] Ninguna consulta a datos de otro modulo: solo IDs y snapshots
- [ ] Clases y metodos nuevos comentados con su ID de trazabilidad
- [ ] Sin secretos en el repo
- [ ] Si el codigo se aparto de PLAN.md, quedo registrado abajo

## Desvios de PLAN.md

<!-- "Ninguno" o que se cambio y por que -->

Ninguno.
