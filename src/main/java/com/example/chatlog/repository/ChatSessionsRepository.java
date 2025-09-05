package com.example.chatlog.repository;

import com.example.chatlog.entity.ChatDates;
import com.example.chatlog.entity.ChatSessions;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionsRepository extends JpaRepository<ChatSessions, Long> {

}
