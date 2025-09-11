package com.example.chatlog.service.impl;

import com.example.chatlog.service.LogApiService;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

@Service
public class LogApiServiceImpl implements LogApiService {

    // WebClient gọi tới Elasticsearch (đã cấu hình SSL trust-all cho môi trường nội bộ)
    private final WebClient webClient;



    // Khởi tạo WebClient với baseUrl và Authorization từ cấu hình
    public LogApiServiceImpl(WebClient.Builder builder,
                             @Value("${elastic.api.url}") String baseUrl,
                             @Value("${elastic.api.key}") String apiKey) {
        HttpClient httpClient = HttpClient.create().secure(ssl -> {
            try {
                ssl.sslContext(
                        SslContextBuilder.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build()
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        this.webClient = builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "ApiKey " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("kbn-xsrf", "true")
                .build();
    }

    @Override
    public String search(String index,String body) {
        // Gọi Elasticsearch _search. Hiện vẫn dùng GET theo setup hiện tại.
        System.out.println("[LogApiServiceImpl] _search index=" + index);
        System.out.println("[LogApiServiceImpl] _search body (truncated 1k): " + (body != null && body.length() > 1000 ? body.substring(0,1000) + "..." : body));
        return webClient
                .method(HttpMethod.GET)
                .uri("/" + index + "/_search")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    @Override
    public String getFieldLog(String index) {
        System.out.println("[LogApiServiceImpl] Fetch _mapping for index=" + index);
        return webClient
                .method(HttpMethod.GET)
                .uri("/"+index+"/_mapping")
                .retrieve()
                .bodyToMono(String.class)
                .block();

    }
}
