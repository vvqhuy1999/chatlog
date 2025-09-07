
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

# Tạo stage mới cho runtime (Tomcat để chạy WAR)
FROM tomcat:10.1-jdk21-temurin

# Thiết lập JVM options (tuỳ chỉnh theo nhu cầu)
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Dọn mặc định và copy WAR làm ROOT.war
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=builder /app/target/chatlog-0.0.1-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war

# Expose port (Tomcat mặc định là 8080)
EXPOSE 8080

# Chạy Tomcat
CMD ["catalina.sh", "run"]
