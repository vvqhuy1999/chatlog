
# Sử dụng Eclipse Temurin 21 làm base image
FROM eclipse-temurin:21-jdk-alpine as builder

# Cài đặt Maven
RUN apk add --no-cache maven

# Thiết lập working directory
WORKDIR /app

# Copy pom.xml trước để tận dụng Docker layer caching
COPY pom.xml .

# Download dependencies (tận dụng Docker cache)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build ứng dụng
RUN mvn clean package -DskipTests

# Tạo stage mới cho runtime (multi-stage build để giảm kích thước image)
FROM eclipse-temurin:21-jre-alpine

# Thiết lập working directory
WORKDIR /app

# Copy WAR file từ build stage
COPY --from=builder /app/target/chatlog-0.0.1-SNAPSHOT.war app.war

# Expose port (Spring Boot mặc định là 8080)
EXPOSE 8080

# Thiết lập JVM options (tuỳ chỉnh theo nhu cầu)
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Chạy ứng dụng
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.war"]
