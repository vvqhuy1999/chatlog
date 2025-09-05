package com.example.chatlog.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
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
    
    // Foreign key relationship với ChatDates entity
    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_date", referencedColumnName = "chat_date", 
                foreignKey = @ForeignKey(name = "fk_session_date"))
    private ChatDates chatDates;
    
    // Helper method để lấy chat_date từ quan hệ
    public LocalDate getChatDate() {
        return chatDates != null ? chatDates.getChatDate() : null;
    }

    // Quan hệ OneToMany với chat_messages
    @JsonManagedReference
    @OneToMany(mappedBy = "chatSessions", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChatMessages> chatMessages;
}


