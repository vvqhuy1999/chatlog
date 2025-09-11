"# chatlog"

## Cấu trúc dự án (Spring Boot)

Lưu ý: Bỏ qua thư mục build như `target/`.

```text
chatlog/
├─ pom.xml
├─ Dockerfile
├─ docker-compose.yml
├─ .dockerignore
├─ .gitattributes
├─ .gitignore
├─ chatlog.sql
├─ README.md
├─ src/
│  ├─ main/
│  │  ├─ java/
│  │  │  └─ com/example/chatlog/
│  │  │     ├─ ChatlogApplication.java
│  │  │     ├─ controller/
│  │  │     │  ├─ AuthController.java
│  │  │     │  ├─ ChatMessagesController.java
│  │  │     │  └─ ChatSessionsController.java
│  │  │     ├─ dto/
│  │  │     │  ├─ ChatRequest.java
│  │  │     │  └─ StartSessionRequest.java
│  │  │     ├─ entity/
│  │  │     │  ├─ ChatMessages.java
│  │  │     │  └─ ChatSessions.java
│  │  │     ├─ repository/
│  │  │     │  ├─ ChatMessagesRepository.java
│  │  │     │  └─ ChatSessionsRepository.java
│  │  │     ├─ service/
│  │  │     │  ├─ AiService.java
│  │  │     │  ├─ ChatMessagesService.java
│  │  │     │  ├─ ChatSessionsService.java
│  │  │     │  └─ impl/
│  │  │     │     ├─ AiServiceImpl.java
│  │  │     │     ├─ ChatMessagesServiceImpl.java
│  │  │     │     └─ ChatSessionsServiceImpl.java
│  │  └─ resources/
│  │     └─ application.yaml
│  └─ test/
│     └─ java/
│        └─ com/example/chatlog/
│           └─ ChatlogApplicationTests.java
```

## Mô tả tệp và thư mục

- pom.xml: Khai báo dependency, plugin, version JDK cho dự án Spring Boot.
- Dockerfile: Build multi-stage để tạo image chạy ứng dụng Spring Boot (JAR).
- docker-compose.yml: Orchestrate 3 services: postgres, app (Spring Boot), frontend.
- .dockerignore: Loại trừ file/thư mục không cần khi build Docker context.
- .gitattributes: Thiết lập thuộc tính Git (ví dụ normalize EOL).
- .gitignore: Bỏ qua file không commit, đã có rule cho .env.
- chatlog.sql: Schema và seed dữ liệu cho PostgreSQL (bảng chat_sessions, chat_messages, index...).
- README.md: Hướng dẫn cài đặt, deploy (giữ nguyên các ghi chú của bạn).
- src/main/java/com/example/chatlog/ChatlogApplication.java: Điểm khởi động Spring Boot.
- controller/:
  - AuthController.java: Login/check/logout giả lập bằng biến tĩnh.
  - ChatMessagesController.java: REST API CRUD message và tạo message mới cho session.
  - ChatSessionsController.java: REST API cho session, bao gồm endpoint /start tạo session + message đầu.
- dto/:
  - ChatRequest.java: DTO gửi nội dung chat đến AI service.
  - StartSessionRequest.java: DTO nhận content khi tạo session mới.
- entity/:
  - ChatMessages.java: Entity message (sender USER/AI, content, timestamp, liên kết session).
  - ChatSessions.java: Entity session (title, createdAt, lastActiveAt, one-to-many messages).
- repository/:
  - ChatMessagesRepository.java: Truy vấn message theo session, theo thời gian.
  - ChatSessionsRepository.java: CRUD session.
- service/:
  - AiService.java, impl/AiServiceImpl.java: Gọi AI tạo phản hồi.
  - ChatMessagesService.java, impl/ChatMessagesServiceImpl.java: Lưu và sinh response AI cho message.
  - ChatSessionsService.java, impl/ChatSessionsServiceImpl.java: CRUD session và tạo session kèm message đầu.
- src/main/resources/application.yaml: Cấu hình Spring (datasource mặc định, JPA, model AI qua env OPENAI_API_KEY).
- src/test/java/.../ChatlogApplicationTests.java: Test khởi động context cơ bản.


<!-- 

docker tag chatlog:latest vvqhuy1999/chatlog:latest
docker push vvqhuy1999/chatlog:latest 


docker pull vvqhuy1999/chatlog:latest

docker run -d --name chatlog-standalone --network chatlog_chatlog-network -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/chatlog -e SPRING_DATASOURCE_USERNAME=chatlog_user -e SPRING_DATASOURCE_PASSWORD=chatlog_password -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver -e SPRING_JPA_HIBERNATE_DDL_AUTO=update -e SPRING_JPA_SHOW_SQL=true -e SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect vvqhuy1999/chatlog:latest

-->

<!-- 
# Add Docker's official GPG key:
sudo apt-get update
sudo apt-get install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# Add the repository to Apt sources:
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update 



sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo docker run hello-world


sudo docker network create chatlog_chatlog-network


sudo docker run -d --name postgres --network chatlog_chatlog-network \
  -e POSTGRES_DB=chatlog \
  -e POSTGRES_USER=chatlog_user \
  -e POSTGRES_PASSWORD=chatlog_password \
  -v postgres_data:/var/lib/postgresql/data \
  postgres:16-alpine


sudo docker pull vvqhuy1999/chatlog:latest

sudo docker run -d --name chatlog-standalone --network chatlog_chatlog-network -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/chatlog \
  -e SPRING_DATASOURCE_USERNAME=chatlog_user \
  -e SPRING_DATASOURCE_PASSWORD=chatlog_password \
  vvqhuy1999/chatlog:latest


sudo docker cp chatlog.sql postgres:/tmp/chatlog.sql  Lỗi

sudo docker cp /home/httt/chatlog/chatlog.sql postgres:/tmp/chatlog.sql  Lỗi

pwd: /home/httt

ls -l: total 0


docker ps --format "{{.Names}}"
return : chatlog-standalone, postgres

sudo docker exec -it postgres psql -U chatlog_user -d chatlog -c "select current_user, current_database();"
 current_user | current_database
--------------+------------------
 chatlog_user | chatlog
(1 row)
---------------------------------------------------------------------------------



httt@hpt-botlog-srv:~$ sudo docker exec -i postgres psql -U chatlog_user -d chatlog -v ON_ERROR_STOP=1 < /home/httt/chatlog.sql
[sudo] password for httt:
NOTICE:  relation "chat_sessions" already exists, skipping
CREATE TABLE
CREATE TABLE
NOTICE:  relation "chat_messages" already exists, skipping
NOTICE:  relation "idx_chat_sessions_last_active" already exists, skipping
CREATE INDEX
NOTICE:  relation "idx_chat_messages_session_time" already exists, skipping
CREATE INDEX
INSERT 0 1
INSERT 0 1
INSERT 0 1
INSERT 0 4
INSERT 0 3
INSERT 0 3


sudo docker ps -a
sudo docker images
sudo docker volume ls
sudo docker network ls

remove container
sudo docker rm -f $(sudo docker ps -aq) || true
remove image
sudo docker rmi -f $(sudo docker images -q) || true

sudo docker volume rm $(sudo docker volume ls -q) || true
sudo docker network prune -f

create image cho docker
docker build -t chatlog:latest .
docker build -t log-chatbot-frontend:latest .


push image dockerhub
docker tag chatlog:latest vvqhuy1999/chatlog:latest
docker tag log-chatbot-frontend:latest vvqhuy1999/log-chatbot-frontend:latest

docker push vvqhuy1999/chatlog:latest
docker push vvqhuy1999/log-chatbot-frontend:latest

pull về server run file docker-compose.yml
thêm file .env vào 
# trong thư mục chứa docker-compose.yml và .env
sudo docker compose --env-file .env up -d
# nếu đã chạy trước đó và thay đổi biến:
sudo docker compose --env-file .env up -d --force-recreate

# Kiem tra container và tên 
sudo docker ps --format '{{.Names}}\t{{.Image}}'
log-chatbot-frontend    vvqhuy1999/log-chatbot-frontend:latest
chatlog-app     vvqhuy1999/chatlog:latest
chatlog-postgres        postgres:16-alpine


sudo docker exec -e PGPASSWORD=postgres -it chatlog-postgres \
  psql -U postgres -d postgres -c "CREATE DATABASE chatlog OWNER postgres;"

-->
<!--
sudo apt install openjdk-21-jdk
java -version
javac -version


export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' >> ~/.bashrc


sudo apt install maven
mvn -version

`
Apache Maven 3.8.7
Maven home: /usr/share/maven
Java version: 21.0.8, vendor: Ubuntu, runtime: /usr/lib/jvm/java-21-openjdk-amd64
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "6.8.0-41-generic", arch: "amd64", family: "unix"`

sudo apt install postgresql postgresql-contrib


# Kiểm tra version
sudo -u postgres psql -c "SELECT version();"
`PostgreSQL 16.9 (Ubuntu 16.9-0ubuntu0.24.04.1) on x86_64-pc-linux-gnu, compiled by gcc
(Ubuntu 13.3.0-6ubuntu2~24.04) 13.3.0, 64-bit`

# Switch sang postgres user và vào psql
sudo -u postgres psql

-- Tạo database chatlog
CREATE DATABASE chatlog;

-- Kiểm tra user postgres đã tồn tại (mặc định có sẵn)
\du

-- Set password cho user postgres
ALTER USER postgres PASSWORD 'postgres';

-- Grant quyền cho user postgres trên database chatlog
GRANT ALL PRIVILEGES ON DATABASE chatlog TO postgres;

-- Connect vào database chatlog để test
\c chatlog

-- Kiểm tra connection
SELECT current_database(), current_user;

-- Thoát psql
\q


sudo -u postgres psql -d chatlog

# Copy file vào thư mục /tmp
cp /home/httt/chatlog/chatlog.sql /tmp/

# Set quyền cho file
chmod 644 /tmp/chatlog.sql

# Vào lại psql
sudo -u postgres psql -d chatlog

# Import file
\i /tmp/chatlog.sql
# thoat khoi
\q

# kiem tra postgres
sudo ss -tlnp | grep postgres


# vao thu muc du an
cd chatlog


# Chạy dự án
mvn clean install
mvn spring-boot:run



# New tab chạy cho fontend

# Download và chạy script setup cho Node.js 22.x
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -

# Cài đặt Node.js và npm
sudo apt-get install -y nodejs

# Kiểm tra version
node -v    # Should show v22.19.0
npm -v     # Should show 10.9.3

# cài đặt nginx
sudo apt install nginx

# Tạo file cấu hình trong sites-available
sudo nano /etc/nginx/sites-available/log-chatbot

Ctrl + o , enter, Ctrl + Enter

# Tạo lại symbolic link
sudo ln -s /etc/nginx/sites-available/log-chatbot /etc/nginx/sites-enabled/

# Thay đổi quyền sở hữu thư mục dist cho www-data (user mà Nginx chạy)
sudo chown -R www-data:www-data /home/httt/log-chatbot/dist

# Đảm bảo tất cả các file trong thư mục dist có quyền đọc
sudo chmod -R 755 /home/httt/log-chatbot/dist

# restart nginx
sudo systemctl restart nginx


httt@hpt-botlog-srv:~/log-chatbot$ ls -ld /home/httt/log-chatbot/dist
drwxr-xr-x 3 www-data www-data 4096 Sep  9 07:40 /home/httt/log-chatbot/dist
httt@hpt-botlog-srv:~/log-chatbot$ ls -la /home/httt/log-chatbot/dist/index.html
-rwxr-xr-x 1 www-data www-data 440 Sep  9 07:40 /home/httt/log-chatbot/dist/index.html
httt@hpt-botlog-srv:~/log-chatbot$ ls -ld /home/httt/log-chatbot
drwxrwxr-x 8 httt httt 4096 Sep  9 07:18 /home/httt/log-chatbot
httt@hpt-botlog-srv:~/log-chatbot$ ^C
httt@hpt-botlog-srv:~/log-chatbot$ ps aux | grep nginx
root      465580  0.0  0.0  11288  3904 ?        Ss   07:51   0:00 nginx: master process /usr/sbin/nginx -g daemon on; master_process on;
www-data  467145  0.0  0.0  12824  5200 ?        S    07:55   0:00 nginx: worker process
www-data  467146  0.0  0.0  12824  5328 ?        S    07:55   0:00 nginx: worker process
www-data  467147  0.0  0.0  12824  5200 ?        S    07:55   0:00 nginx: worker process
www-data  467148  0.0  0.0  12824  4560 ?        S    07:55   0:00 nginx: worker process
httt      475831  0.0  0.0   6544  2304 pts/3    S+   08:19   0:00 grep --color=auto nginx
httt@hpt-botlog-srv:~/log-chatbot$ ^C
httt@hpt-botlog-srv:~/log-chatbot$ ls -ld /home/httt
drwxr-x--- 9 httt httt 4096 Sep  9 07:07 /home/httt
httt@hpt-botlog-srv:~/log-chatbot$ chmod 755 /home/httt
httt@hpt-botlog-srv:~/log-chatbot$ ls -ld /home/httt
drwxr-xr-x 9 httt httt 4096 Sep  9 07:07 /home/httt
httt@hpt-botlog-srv:~/log-chatbot$ sudo -u www-data test -r /home/httt/log-chatbot/dist/index.html && echo "✅ Có thể đọc index.html" || echo "❌ Vẫn không thể đọc index.html"
✅ Có thể đọc index.html
httt@hpt-botlog-srv:~/log-chatbot$ sudo systemctl restart nginx


-->

