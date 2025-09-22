package com.example.chatlog.service;

import com.example.chatlog.entity.ChatMessages;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public interface ChatMessagesService {
  List<ChatMessages> findAllBySessionId(Long sessionId);
  ChatMessages findById(Long id);
  ChatMessages save(Long sessionId,ChatMessages chatMessages);
  ChatMessages save(ChatMessages chatMessages);
  ChatMessages saveWithoutAiResponse(Long sessionId, ChatMessages chatMessages);
  void deleteById(Long id);
}
