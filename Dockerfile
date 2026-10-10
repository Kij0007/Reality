FROM maven:3.9.13-eclipse-temurin-21-noble AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src/main ./src/main
RUN mvn --batch-mode --no-transfer-progress -DskipTests package \
    && cp target/reality-*.jar /workspace/reality.jar

FROM eclipse-temurin:21-jre-noble AS runtime
WORKDIR /app
ENV HOME=/app \
    SPRING_PROFILES_ACTIVE=cloud \
    TZ=Asia/Kolkata \
    JAVA_TOOL_OPTIONS="-Xms32m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=16m -Xss512k -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Kolkata"
COPY --from=build --chown=10001:10001 /workspace/reality.jar /app/reality.jar
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/reality.jar"]
