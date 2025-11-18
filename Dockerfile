FROM maven:3.9.11-eclipse-temurin-17@sha256:aaa8835df17b80a18a863456bc6ccd05e0ace19b92376a27ee0e6efd6bc10ec6 AS build
WORKDIR /build
COPY lib ./lib
COPY pom.xml ./pom.xml
COPY org.hl7.fhir.publisher.cli/pom.xml ./org.hl7.fhir.publisher.cli/pom.xml
COPY org.hl7.fhir.publisher.core/pom.xml ./org.hl7.fhir.publisher.core/pom.xml
RUN mvn dependency:go-offline
COPY . .
RUN mvn install -Dmaven.test.skip=true

FROM eclipse-temurin:17@sha256:a65c820d885b3c05bf0fadc516c2da6fe1cfc0eeae61880a6c9594d6d64cc118
WORKDIR /app

USER root

ENV APPLICATION_USER=igpublisher
RUN adduser $APPLICATION_USER

RUN chown -R $APPLICATION_USER /app

RUN mkdir /home/$APPLICATION_USER/.fhir
RUN chown -R $APPLICATION_USER /home/$APPLICATION_USER/.fhir

RUN apt-get update && \
    apt-get install -y --no-install-recommends git ruby-full build-essential zlib1g-dev nodejs npm && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    npm install -g fsh-sushi@3.16.5 && \
    gem install jekyll

USER $APPLICATION_USER
COPY --from=build /build/org.hl7.fhir.publisher.cli/target/org.hl7.fhir.publisher.cli-*-SNAPSHOT.jar /app/org.hl7.fhir.publisher.cli.jar
HEALTHCHECK CMD java -jar /app/org.hl7.fhir.publisher.cli.jar
ENTRYPOINT ["java", "-jar", "/app/org.hl7.fhir.publisher.cli.jar"]