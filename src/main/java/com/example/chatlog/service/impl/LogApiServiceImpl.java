package com.example.chatlog.service.impl;

import com.example.chatlog.service.LogApiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class LogApiServiceImpl implements LogApiService {

    // WebClient để giao tiếp với Elasticsearch thông qua HTTP/HTTPS
    private final WebClient webClient;

    /**
     * Constructor khởi tạo LogApiServiceImpl với cấu hình kết nối Elasticsearch
     * Thiết lập SSL trust-all cho môi trường nội bộ và các header cần thiết
     *
     * @param builder WebClient.Builder từ Spring Boot
     * @param baseUrl URL của Elasticsearch server (từ application.yaml)
     * @param apiKey API key để xác thực với Elasticsearch (từ application.yaml)
     */
    public LogApiServiceImpl(WebClient.Builder builder,
        @Value("${elastic.api.url}") String baseUrl,
        @Value("${elastic.api.key}") String apiKey) {

        // Cấu hình HTTP client với SSL trust-all (chỉ dùng cho môi trường nội bộ)
        HttpClient httpClient = HttpClient.create().secure(ssl -> {
            try {
                ssl.sslContext(
                    SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE) // Tin tương tất cả certificates
                        .build()
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Xây dựng WebClient với cấu hình SSL và các header mặc định
        this.webClient = builder
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl(baseUrl) // URL cơ sở của Elasticsearch
            .defaultHeader("Authorization", "ApiKey " + apiKey) // Xác thực bằng API key
            .defaultHeader("Content-Type", "application/json") // Định dạng JSON
            .defaultHeader("kbn-xsrf", "true") // Header bảo mật cho Kibana
            .build();
    }

    /**
     * Thực hiện tìm kiếm dữ liệu log trong Elasticsearch
     * Gửi query đến endpoint _search của Elasticsearch và trả về kết quả
     *
     * @param index Tên index cần tìm kiếm (ví dụ: "logs-fortinet_fortigate.log-default*")
     * @param body JSON query body theo chuẩn Elasticsearch Query DSL
     * @return Kết quả tìm kiếm dạng JSON string từ Elasticsearch
     */
    @Override
    public String search(String index,String body) {
     // Gửi HTTP POST request đến Elasticsearch _search endpoint
        return webClient.post()
            .uri("/" + index + "/_search") // Đường dẫn tìm kiếm của Elasticsearch
            .bodyValue(body) // JSON query body
            .retrieve() // Thực hiện request
            .bodyToMono(String.class) // Chuyển đổi response thành String
            .block(); // Chờ kết quả (blocking call)
    }

    /**
     * Lấy thông tin mapping (cấu trúc field) của Elasticsearch index
     * Mapping chứa thông tin về các field có trong index và kiểu dữ liệu của chúng
     *
     * @param index Tên index cần lấy mapping (ví dụ: "logs-fortinet_fortigate.log-default*")
     * @return Thông tin mapping dạng JSON string từ Elasticsearch
     */
    @Override
    public String getFieldLog(String index) {
        // Gửi HTTP POST request đến Elasticsearch _mapping endpoint
        // Gửi HTTP POST request đến Elasticsearch _mapping endpoint
        return webClient
                .method(HttpMethod.GET)
            .uri("/"+index+"/_mapping") // Đường dẫn mapping của Elasticsearch
            .retrieve() // Thực hiện request
            .bodyToMono(String.class) // Chuyển đổi response thành String
            .block(); // Chờ kết quả (blocking call)
    }
    @Override
    public String getAllField(String index){
        String json = webClient
                .post()
                .uri("/"+index+"/_field_caps?fields=*")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode fieldsNode = root.get("fields");

            List<String> fieldNames = new ArrayList<>();
            if (fieldsNode != null) {
                Iterator<String> it = fieldsNode.fieldNames();
                while (it.hasNext()) {
                    fieldNames.add(it.next());
                }
            }

//            System.out.println("Danh sách field:");
//            System.out.println("Danh sách field:");
            // fieldNames.forEach(System.out::println);
            return fieldNames.toString();
        } catch (Exception e) {
            System.out.println("[LogApiServiceImpl] : " +e.getMessage());
            System.out.println("[LogApiServiceImpl] : " +e.getMessage());
        }
        return "";
    }
}