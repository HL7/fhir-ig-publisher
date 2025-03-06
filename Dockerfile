FROM maven:3.6.3-jdk-11 AS build
WORKDIR /build
COPY lib ./lib
COPY pom.xml ./pom.xml
COPY org.hl7.fhir.publisher.cli/pom.xml ./org.hl7.fhir.publisher.cli/pom.xml
COPY org.hl7.fhir.publisher.core/pom.xml ./org.hl7.fhir.publisher.core/pom.xml
RUN mvn dependency:go-offline
COPY . .
RUN mvn install -Dmaven.test.skip=true

FROM eclipse-temurin:11
WORKDIR /usr/src/app

RUN apt-get update && \
    apt-get install -y --no-install-recommends git ruby-full build-essential zlib1g-dev && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    gem install jekyll

COPY --from=build /build/org.hl7.fhir.publisher.cli/target/org.hl7.fhir.publisher.cli-*-SNAPSHOT.jar /usr/src/app/org.hl7.fhir.publisher.cli.jar
ENTRYPOINT ["java", "-jar", "/usr/src/app/org.hl7.fhir.publisher.cli.jar"]