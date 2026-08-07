# Backend de CareLink — ADR-012 (reproducibilidad local con Docker Compose).
# Multi-stage: el JDK 21 queda en la etapa de build, la imagen final solo lleva JRE.

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# El POM padre y el del módulo primero: si no cambian, la capa de dependencias
# se reutiliza entre builds.
COPY pom.xml ./pom.xml
COPY .mvn ./.mvn
COPY mvnw ./mvnw
COPY services/identity-service/pom.xml ./services/identity-service/pom.xml
RUN ./mvnw -B -f services/identity-service/pom.xml dependency:go-offline

COPY services/identity-service/src ./services/identity-service/src
RUN ./mvnw -B -f services/identity-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Usuario sin privilegios: nada en este contenedor necesita root.
RUN useradd --system --uid 10001 --create-home carelink
USER carelink

# `-exec.jar`, no `*.jar`: el jar plano y el ejecutable conviven en target/ (ver el
# clasificador en el pom). Copiar el plano produce un contenedor que arranca y muere
# por NoClassDefFoundError sobre Spring.
COPY --from=build --chown=carelink:carelink \
     /build/services/identity-service/target/identity-service-*-exec.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
