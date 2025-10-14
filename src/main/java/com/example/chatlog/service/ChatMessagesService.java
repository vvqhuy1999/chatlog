package com.example.chatlog.service;

import com.example.chatlog.entity.ChatMessages;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public interface ChatMessagesService {
  List<ChatMessages> findAllBySessionId(Long sessionId);
  ChatMessages save(Long sessionId,ChatMessages chatMessages);
  ChatMessages saveWithoutAiResponse(Long sessionId, ChatMessages chatMessages);
}
