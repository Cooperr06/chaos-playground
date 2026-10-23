FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY shop-service/pom.xml shop-service/
COPY inventory-service/pom.xml inventory-service/
COPY payment-service/pom.xml payment-service/
COPY chaos-controller/pom.xml chaos-controller/
COPY load-generator/pom.xml load-generator/
COPY payment-service/src payment-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl payment-service -am -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-alpine
RUN adduser -D -H app
USER app
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
