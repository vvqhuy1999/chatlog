package com.example.chatlog.service;

import com.example.chatlog.entity.ChatDates;
import com.example.chatlog.entity.ChatMessages;
import java.util.List;
import org.springframework.stereotype.Service;


public interface ChatMessagesService {
  List<ChatMessages> findAll();
  ChatMessages findById(Long id);
  ChatMessages save(ChatMessages chatMessages);
  void deleteById(Long id);
}
