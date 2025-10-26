package com.example.chatlog.entity.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "chat_sessions")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data

@NoArgsConstructor
@AllArgsConstructor
public class ChatSessions implements Serializable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id", nullable = false)
    private Long sessionId;
    
    @Column(name = "title", length = 255, columnDefinition = "VARCHAR(255) DEFAULT 'Cuộc trò chuyện mới'")
    private String title = "Cuộc trò chuyện mới";
    
    @Column(name = "created_at", updatable = false, insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "last_active_at", insertable = false,
            columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime lastActiveAt;

    // Quan hệ OneToMany với chat_messages
    @JsonIgnore
    @OneToMany(mappedBy = "chatSessions", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChatMessages> chatMessages;
}


