FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY src /workspace/src

RUN mvn -q -DskipTests -Ddependency.check.skip=true package

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*
RUN addgroup --system elvo && adduser --system --ingroup elvo elvo

COPY --from=build /workspace/target/*.jar /app/app.jar

USER elvo

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
