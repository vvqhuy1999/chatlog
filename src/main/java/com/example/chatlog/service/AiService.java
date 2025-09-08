package com.example.chatlog.service;


import com.example.chatlog.dto.ChatRequest;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public interface AiService {

    String getAiResponse(Long sessionId,ChatRequest request);

    String getAiResponse(Long sessionId,MultipartFile file, ChatRequest request);
}
