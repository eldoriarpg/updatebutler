FROM eclipse-temurin:18-alpine as build

COPY . .
RUN ./gradlew clean build

FROM eclipse-temurin:19-alpine as runtime

WORKDIR /app

COPY --from=build /build/libs/UpdateButler-*-all.jar bot.jar
COPY src/main/resources/log4j2.xml config/log4j2.xml

ENTRYPOINT ["java", "-Dlog4j.configurationFile=config/log4j2.xml", "-jar" , "bot.jar"]
