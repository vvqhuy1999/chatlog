package com.example.chatlog.service;


import com.example.chatlog.dto.ChatRequest;
import org.springframework.stereotype.Service;

@Service
public interface AiService {

    String getAiResponse(ChatRequest request);

}
