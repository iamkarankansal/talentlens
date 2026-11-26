FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system talentlens \
    && useradd --system --gid talentlens --no-create-home --shell /usr/sbin/nologin talentlens
WORKDIR /app
COPY --from=build /app/target/talentlens-*.jar app.jar
USER talentlens
# Size the heap from the container's memory limit and exit on OOM so the orchestrator restarts it.
# Override or extend with JAVA_TOOL_OPTIONS at run time.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
