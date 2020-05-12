FROM maven:3.6.3-jdk-11 as build
WORKDIR /build
COPY lib ./lib
COPY pom.xml ./pom.xml
COPY org.hl7.fhir.publisher.cli/pom.xml ./org.hl7.fhir.publisher.cli/pom.xml
COPY org.hl7.fhir.publisher.core/pom.xml ./org.hl7.fhir.publisher.core/pom.xml
RUN mvn dependency:go-offline
COPY . .
RUN mvn install

FROM openjdk:11-jre-buster
WORKDIR /usr/src/app

RUN apt-get update && \
    apt-get install -y --no-install-recommends ruby ruby-all-dev build-essential && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    gem install jekyll

COPY --from=build /build/org.hl7.fhir.publisher.cli/target/org.hl7.fhir.publisher.cli-*-SNAPSHOT.jar /usr/src/app/org.hl7.fhir.publisher.cli.jar
ENTRYPOINT ["java", "-jar", "/usr/src/app/org.hl7.fhir.publisher.cli.jar"]
CMD ["-?"]
