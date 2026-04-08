FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace

# Project-safe runtime packaging: use prebuilt service artifact from target/
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} /workspace/app.jar

FROM eclipse-temurin:17-jre

WORKDIR /app

# Run as non-root for better container security posture
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*
RUN addgroup --system elvo && adduser --system --ingroup elvo elvo

COPY --from=build /workspace/app.jar /app/app.jar

USER elvo

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
