# Etapa de compilación (Build)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copiamos el wrapper de Maven y los archivos de configuración
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Damos permisos de ejecución al wrapper
RUN chmod +x ./mvnw

# Descargamos las dependencias (opcional, ayuda a cachear capas de Docker)
RUN ./mvnw dependency:go-offline

# Copiamos el código fuente
COPY src src

# Compilamos el proyecto (saltando los tests para que el deploy sea más rápido)
RUN ./mvnw clean package -DskipTests

# Etapa de ejecución (Run)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiamos el .jar generado en la etapa de build
COPY --from=build /app/target/ifts2-backend-0.0.1-SNAPSHOT.jar app.jar

# Exponemos el puerto que usa Spring Boot por defecto
EXPOSE 8080

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]
