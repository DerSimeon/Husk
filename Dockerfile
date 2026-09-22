# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN sh ./gradlew :husk-server:shadowJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre
COPY --from=build /src/husk-server/build/libs/husk-server-*-all.jar /opt/husk/husk.jar
# husk.conf and roots/ are created here on first start; mount a volume to keep them.
WORKDIR /data
VOLUME /data
EXPOSE 25565
ENTRYPOINT ["java", "-jar", "/opt/husk/husk.jar"]
