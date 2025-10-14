package com.example.chatlog.service;


import org.springframework.stereotype.Service;

@Service
public interface LogApiService {
    String search(String index,String body);


    String getAllField(String index);
}
