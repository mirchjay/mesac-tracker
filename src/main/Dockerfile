FROM gradle:8-jdk21
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon
CMD ["sh", "-c", "java -jar build/libs/*.jar"]
