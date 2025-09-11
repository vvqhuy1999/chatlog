package com.example.chatlog.service;


import org.springframework.stereotype.Service;

@Service
public interface LogApiService {
    String search(String index,String body);

    String getFieldLog(String index);

    String getAllField(String index);
}
