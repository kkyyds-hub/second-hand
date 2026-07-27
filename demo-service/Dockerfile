FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl demo-service -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy
ENV TZ=Asia/Shanghai
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && apt-get clean && rm -rf /var/lib/apt/lists/* \
    && groupadd --system secondhand && useradd --system --gid secondhand --home-dir /app secondhand \
    && mkdir -p /data/uploads && chown -R secondhand:secondhand /app /data/uploads
COPY --from=build /workspace/demo-service/target/*.jar /app/app.jar
USER secondhand
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /app/app.jar"]
