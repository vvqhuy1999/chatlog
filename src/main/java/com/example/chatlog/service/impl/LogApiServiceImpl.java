package com.example.chatlog.service.impl;

import com.example.chatlog.service.LogApiService;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.io.Console;
import java.util.logging.Logger;

@Service
public class LogApiServiceImpl implements LogApiService {

    private final WebClient webClient;



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
    public String searchByDate(String index, String gte, String lte) {
        String body = """
        {
          "query": { "range": { "@timestamp": { "gte": "%s", "lte": "%s" } } },
          "size" : 10,
          "sort": [{ "@timestamp": { "order": "desc" } }]
        }
        """.formatted(gte, lte);
        System.out.println(body);
        return webClient.post()
                .uri("/" + index + "/_search")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}
