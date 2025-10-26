package com.example.chatlog.repository;

import com.example.chatlog.entity.chat.ChatMessages;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ChatMessagesRepository extends JpaRepository<ChatMessages, Long> {
    List<ChatMessages> findByChatSessionsSessionIdOrderByTimestampAsc(Long sessionId);
    List<ChatMessages> findAllByChatSessions_SessionId(Long sessionId);
    
    // Method đã sửa với đúng cú pháp Spring Data JPA - sắp xếp timestamp giảm dần (DESC)
    List<ChatMessages> findTop10ByChatSessionsSessionIdOrderByTimestampDesc(Long sessionId);
    
    // Alternative method với @Query để đảm bảo hoạt động đúng
    @Query("SELECT cm FROM ChatMessages cm WHERE cm.chatSessions.sessionId = :sessionId ORDER BY cm.timestamp DESC")
    List<ChatMessages> findTop10MessagesBySessionIdOrderByTimestampDesc(@Param("sessionId") Long sessionId, Pageable pageable);
    
    // Method với JOIN FETCH để tránh lazy loading issues
    @Query("SELECT cm FROM ChatMessages cm JOIN FETCH cm.chatSessions cs WHERE cs.sessionId = :sessionId ORDER BY cm.timestamp DESC")
    List<ChatMessages> findTop10MessagesBySessionIdWithSession(@Param("sessionId") Long sessionId, Pageable pageable);
}
