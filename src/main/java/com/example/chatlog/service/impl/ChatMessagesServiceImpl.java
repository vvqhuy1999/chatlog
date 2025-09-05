package com.example.chatlog.service.impl;

import com.example.chatlog.entity.ChatMessages;
import com.example.chatlog.repository.ChatMessagesRepository;
import com.example.chatlog.service.ChatMessagesService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatMessagesServiceImpl implements ChatMessagesService {

  @Autowired
  private ChatMessagesRepository chatMessagesRepository;

  @Override
  public List<ChatMessages> findAll() {
    return chatMessagesRepository.findAll();
  }

  @Override
  public ChatMessages findById(Long id) {
    return chatMessagesRepository.findById(id).orElse(null);
  }

  @Override
  public ChatMessages save(ChatMessages chatMessages) {
    return chatMessagesRepository.save(chatMessages);
  }

  @Override
  public void deleteById(Long id) {
    chatMessagesRepository.deleteById(id);
  }
}
