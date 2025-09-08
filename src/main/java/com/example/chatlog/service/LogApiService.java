package com.example.chatlog.service;


import org.springframework.stereotype.Service;

@Service
public interface LogApiService {
    String searchByDate(String index,String body);
}
