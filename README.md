"# chatlog" 

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

kiem tra container và tên 
sudo docker ps --format '{{.Names}}\t{{.Image}}'
log-chatbot-frontend    vvqhuy1999/log-chatbot-frontend:latest
chatlog-app     vvqhuy1999/chatlog:latest
chatlog-postgres        postgres:16-alpine


sudo docker exec -e PGPASSWORD=postgres -it chatlog-postgres \
  psql -U postgres -d postgres -c "CREATE DATABASE chatlog OWNER postgres;"

-->