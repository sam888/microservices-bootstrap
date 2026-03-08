# Use the Gradle image as a build stage
FROM gradle:jdk17-alpine as builder

# Create a system group named 'spring' with GID 1001, then create a system user named 'spring' with UID 1001 and assign
# them to that group.
RUN addgroup -g 1001 -S spring && adduser -u 1001 -S spring -G spring

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

# Install timezone data (still as root)
RUN apk add --no-cache tzdata

# Switch to UID 1001. All subsequent commands and the final runtime will operate with non-root privileges.
USER 1001

# Use JSON 'exec' form with a shell wrapper to achieve three things:
# 1. ["/bin/sh", "-c"] : Uses the exec form to start the shell directly.
# 2. $JAVA_OPTS        : Allows the shell to expand environment variables from K8s.
# 3. exec java  : Replaces the shell with the Java process so Java becomes PID 1. Without this, shell gets SIGTERM signal
#                 instead of Java process so after terminationGracePeriodSeconds runs out, K8s loses patience and sends a
#                 SIGKILL to nuke the container(shell and Java) instantly => Active transactions cut off and user sees
#                 502 Bad Gateway returned
# This ensures Java receives SIGTERM signals from Kubernetes for graceful shutdown.
ENTRYPOINT ["/bin/sh", "-c", "exec java -Djava.security.egd=file:/dev/./urandom -jar /app.jar"]
