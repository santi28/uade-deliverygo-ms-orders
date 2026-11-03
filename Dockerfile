# Imagen del modulo Pedidos. Dos etapas: se compila con el JDK y se corre con el JRE,
# para no cargar el compilador ni el repositorio de Maven en la imagen final.
#
# Construir y levantar junto con la base:
#   docker compose up --build
#
# Construir sola:
#   docker build -t deliverygo/orders-ms .

# ---------------------------------------------------------------- etapa de compilacion
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# Las dependencias se copian y resuelven antes del codigo: mientras el pom no cambie,
# Docker reutiliza esta capa y no vuelve a bajar medio Maven en cada build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Los tests corren en la maquina de desarrollo y en CI (./mvnw test), no en el build de
# la imagen: aca solo se empaqueta lo que ya esta en verde.
RUN ./mvnw -B -q clean package -DskipTests

# -------------------------------------------------------------------- imagen de runtime
FROM eclipse-temurin:21-jre-alpine

# Usuario sin privilegios: la app no necesita root y asi un escape del proceso no se
# lleva el contenedor.
RUN addgroup -S pedidos && adduser -S pedidos -G pedidos

WORKDIR /app
COPY --from=build /build/target/orders-ms-*.jar app.jar
RUN chown -R pedidos:pedidos /app
USER pedidos

# El perfil por defecto del proyecto ya es dev (Postgres); se cambia con
# SPRING_PROFILES_ACTIVE.
EXPOSE 8080

# Arranque en exec form: el JVM queda como PID 1 y recibe el SIGTERM de `docker stop`,
# asi Spring cierra el pool de conexiones en vez de morir de golpe.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
