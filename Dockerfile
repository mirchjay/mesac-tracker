FROM maven:3.9-eclipse-temurin-21
WORKDIR /app
COPY . .
RUN mvn -q -DskipTests package
CMD ["sh", "-c", "java -jar target/*.jar"]
