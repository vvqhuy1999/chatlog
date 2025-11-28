package com.example.chatlog.service;


import com.example.chatlog.dto.ChatRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public interface AiService {


    Map<String, Object> handleRequestWithComparison(Long sessionId, ChatRequest chatRequest);

}
