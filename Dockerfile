# Stage 1: build the .JAR file
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: run with Java + Python programming languages
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && \
    apt-get install -y python3 python3-pip --no-install-recommends && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

COPY ml/requirements.txt ./ml/requirements.txt
RUN pip3 install --no-cache-dir -r ml/requirements.txt

COPY ml/forecast.py ./ml/forecast.py
COPY --from=builder /build/target/currency-exchange-1.0.0.jar app.jar
RUN mkdir -p /app/data/exports

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]