# Use the Gradle image as a build stage
FROM gradle:jdk17-alpine as builder

# Set the working directory
WORKDIR /app

# Copy the Gradle project files
COPY . /app/

# Build the application with Gradle
RUN gradle clean bootJar

# Final image stage
FROM eclipse-temurin:17-alpine

# Copy the application JAR from the build stage
COPY --from=builder /app/build/libs/*.jar /app.jar

# Install timezone data
RUN apk add --no-cache tzdata

# Set Java environment options
ARG JAVA_ENV='-Xmx256m -Xscmx128m -Xscmaxaot100m -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+HeapDumpOnOutOfMemoryError'
ENV JAVA_OPS=$JAVA_ENV

# Specify the entry point for the application
ENTRYPOINT exec java -Djava.security.egd=file:/dev/./urandom -jar /app.jar
