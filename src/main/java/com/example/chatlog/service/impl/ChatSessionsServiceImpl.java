package com.example.chatlog.service.impl;

import com.example.chatlog.entity.ChatSessions;
import com.example.chatlog.repository.ChatDatesRepository;
import com.example.chatlog.repository.ChatSessionsRepository;
import com.example.chatlog.service.ChatSessionsService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionsServiceImpl implements ChatSessionsService {

  @Autowired
  private ChatSessionsRepository chatSessionsRepository;

  @Override
  public List<ChatSessions> findAll() {
    return chatSessionsRepository.findAll();
  }

  @Override
  public ChatSessions findById(Long id) {
    return chatSessionsRepository.findById(id).orElse(null);
  }

  @Override
  public ChatSessions save(ChatSessions chatSession) {
    return chatSessionsRepository.save(chatSession);
  }

  @Override
  public void deleteById(Long id) {
    chatSessionsRepository.deleteById(id);
  }
}
