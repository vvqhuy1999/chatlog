package com.example.chatlog.repository;

import com.example.chatlog.entity.chat.ChatSessions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionsRepository extends JpaRepository<ChatSessions, Long> {
}
