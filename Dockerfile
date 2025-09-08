
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

# Tạo stage mới cho runtime (chạy Spring Boot JAR)
FROM eclipse-temurin:21-jre-alpine

# Thiết lập JVM options (tuỳ chỉnh theo nhu cầu)
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Thư mục làm việc
WORKDIR /app

# Copy JAR đã build từ stage builder
COPY --from=builder /app/target/chatlog-0.0.1-SNAPSHOT.jar /app/app.jar

# Expose port (Spring Boot mặc định là 8080)
EXPOSE 8080

# Chạy ứng dụng Spring Boot
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
