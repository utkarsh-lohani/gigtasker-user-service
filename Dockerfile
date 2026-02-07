FROM eclipse-temurin:25-jdk

RUN dnf install -y --allowerasing curl

# Copy the built jar
COPY target/*.jar app.jar

# Run the app
ENTRYPOINT ["java", "-jar", "/app.jar"]