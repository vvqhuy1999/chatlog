package com.example.chatlog.service;

import com.example.chatlog.entity.ChatSessions;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public interface ChatSessionsService {

  List<ChatSessions> findAll();
  ChatSessions findById(Long id);
  ChatSessions save(ChatSessions chatSession);
  void deleteById(Long id);
}
