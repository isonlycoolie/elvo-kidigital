FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# Build from source so image creation does not depend on local target/ artifacts.
COPY pom.xml /workspace/pom.xml
COPY shared /workspace/shared
COPY services/elvo-identity-service /workspace/services/elvo-identity-service

RUN mvn -q -pl services/elvo-identity-service -am -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

# Run as non-root for better container security posture
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*
RUN addgroup --system elvo && adduser --system --ingroup elvo elvo

COPY --from=build /workspace/services/elvo-identity-service/target/*.jar /app/app.jar

USER elvo

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
