FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN javac -cp "lib/mysql-connector-j-9.7.0.jar" -d bin src/*.java
EXPOSE 8080
CMD ["java", "-cp", "bin:lib/mysql-connector-j-9.7.0.jar", "ServidorPilates"]
