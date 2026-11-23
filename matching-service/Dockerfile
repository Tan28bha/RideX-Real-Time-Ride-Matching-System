#matching-service/Dockerfile

# step 1: use java 17 as base image
FROM eclipse-temurin:17-jre-alpine

#step2: set working directory inside container
WORKDIR /app

# step 3: copy the built jar file
COPY target/matching-service-0.0.1-SNAPSHOT.jar app.jar

# step 4: Expose port
EXPOSE 8084

# step 5: run the jar
ENTRYPOINT ["java","-jar","app.jar"]
