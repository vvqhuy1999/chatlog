-- =============================================
-- Chức năng: Định nghĩa cấu trúc database để lưu trữ các cuộc trò chuyện.
-- Bao gồm 3 bảng chính:
-- 1. chat_dates: Quản lý các ngày có chat và trạng thái ghim (pin).
-- 2. chat_sessions: Quản lý các phiên chat (cuộc trò chuyện) trong một ngày.
-- 3. chat_messages: Lưu trữ nội dung tin nhắn của từng phiên chat.
-- =============================================

-- Bước 1: Tạo database nếu chưa tồn tại
CREATE DATABASE IF NOT EXISTS `chatlog` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Bước 2: Chọn database vừa tạo để làm việc
USE `chatlog`;

-- Tắt kiểm tra khóa ngoại để tạo bảng dễ dàng
SET FOREIGN_KEY_CHECKS=0;

-- ----- Bảng 1: chat_dates -----
-- Bảng này dùng để quản lý các ngày và trạng thái "ghim".
-- Việc ghim sẽ được áp dụng cho cả một ngày.
CREATE TABLE IF NOT EXISTS `chat_dates` (
  `chat_date` DATE NOT NULL,
  `is_pinned` BOOLEAN NOT NULL DEFAULT FALSE,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`chat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----- Bảng 2: chat_sessions -----
-- Mỗi "New Chat" của người dùng sẽ là một dòng trong bảng này.
-- Bảng này liên kết với `chat_dates` qua cột `chat_date`.
CREATE TABLE IF NOT EXISTS `chat_sessions` (
  `session_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `chat_date` DATE NOT NULL,
  `title` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'Cuộc trò chuyện mới',
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`),
  CONSTRAINT `fk_session_date`
    FOREIGN KEY (`chat_date`)
    REFERENCES `chat_dates` (`chat_date`)
    ON DELETE CASCADE -- Quan trọng: Nếu một ngày bị xóa, tất cả session của ngày đó cũng bị xóa.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----- Bảng 3: chat_messages -----
-- Bảng này chứa nội dung chi tiết của các cuộc trò chuyện.
-- Liên kết với `chat_sessions` qua `session_id`.
CREATE TABLE IF NOT EXISTS `chat_messages` (
  `message_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id` BIGINT UNSIGNED NOT NULL,
  `sender` ENUM('user', 'ai') NOT NULL,
  `content` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`message_id`),
  CONSTRAINT `fk_message_session`
    FOREIGN KEY (`session_id`)
    REFERENCES `chat_sessions` (`session_id`)
    ON DELETE CASCADE -- Quan trọng: Nếu một session bị xóa, tất cả tin nhắn cũng bị xóa.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Bật lại kiểm tra khóa ngoại
SET FOREIGN_KEY_CHECKS=1;

-- =============================================
-- VÍ DỤ VỀ CÁCH THÊM DỮ LIỆU
-- =============================================

/*
-- Khi người dùng bắt đầu chat trong một ngày mới (ví dụ: 2025-09-03)
-- Bước 1: Thêm ngày mới vào chat_dates (nếu chưa tồn tại)
INSERT IGNORE INTO chat_dates (chat_date) VALUES ('2025-09-03');

-- Bước 2: Tạo một session chat mới cho ngày hôm đó
INSERT INTO chat_sessions (chat_date, title) VALUES ('2025-09-03', 'Hỏi về SQL');

-- Giả sử session vừa tạo có session_id = 1
-- Bước 3: Thêm các tin nhắn vào session đó
INSERT INTO chat_messages (session_id, sender, content) VALUES (1, 'user', 'Làm thế nào để tạo database?');
INSERT INTO chat_messages (session_id, sender, content) VALUES (1, 'ai', 'Bạn cần dùng lệnh CREATE DATABASE...');

-- Khi người dùng tạo thêm một "New Chat" nữa trong cùng ngày 2025-09-03
-- Bước 1: Không cần thêm ngày vì đã có
-- Bước 2: Chỉ cần tạo session mới
INSERT INTO chat_sessions (chat_date, title) VALUES ('2025-09-03', 'Lên kế hoạch du lịch');
-- Giả sử session này có id = 2, và cứ thế thêm tin nhắn vào session 2.
*/
/*
select * from chat_dates;
select * from chat_sessions;
select * from chat_messages;

*/

-- =============================================
-- Chức năng: Tạo một sự kiện (Event) trong MySQL để tự động chạy mỗi ngày.
-- Nhiệm vụ: Xóa các ngày chat cũ hơn 5 ngày và không được ghim.
-- Do đã thiết lập ON DELETE CASCADE, khi một ngày bị xóa,
-- các session và message liên quan cũng sẽ tự động bị xóa theo.
-- =============================================

-- LƯU Ý QUAN TRỌNG:
-- Bạn cần đảm bảo rằng Event Scheduler của MySQL đã được bật.
-- Chạy lệnh này để kiểm tra: SHOW VARIABLES LIKE 'event_scheduler';
-- Nếu giá trị là OFF, chạy lệnh này để bật nó lên:
-- SET GLOBAL event_scheduler = ON;

-- Quan trọng: Chọn database để làm việc trước khi tạo Event.
-- Nếu bạn dùng tên khác, hãy thay `chat_app_db` bằng tên database của bạn.

-- Thay đổi ký tự kết thúc lệnh (delimiter) để MySQL có thể xử lý đúng khối BEGIN...END.
DELIMITER $$ 

-- Định nghĩa Event dọn dẹp
CREATE EVENT IF NOT EXISTS `daily_chat_cleanup`
ON SCHEDULE
    -- Chạy mỗi ngày
    EVERY 1 DAY
    -- Bắt đầu từ ngày mai vào lúc 2 giờ sáng (thời điểm ít tải)
    STARTS (CURDATE() + INTERVAL 1 DAY + INTERVAL 2 HOUR)
DO
BEGIN
    -- LOGIC MỚI: Giữ lại 5 ngày có chat gần nhất (không tính theo ngày lịch)
    -- Thay vì xóa dựa trên ngày hiện tại (CURDATE), logic này sẽ:
    -- 1. Tìm ra ngày chat thứ 5 gần nhất mà không bị ghim.
    -- 2. Xóa tất cả các ngày chat cũ hơn ngày đó.
    -- Điều này đảm bảo bạn luôn giữ lại được 5 ngày hoạt động cuối cùng,
    -- ngay cả khi bạn không chat trong nhiều ngày liên tiếp.

    DECLARE cutoff_date DATE;

    -- Tìm ngày chốt (ngày thứ 5 gần nhất không bị ghim)
    -- LIMIT 1 OFFSET 4: Sắp xếp giảm dần, bỏ qua 4 ngày đầu -> lấy ngày thứ 5.
    SELECT `chat_date` INTO cutoff_date
    FROM `chat_dates` 
    WHERE `is_pinned` = FALSE
    ORDER BY `chat_date` DESC
    LIMIT 1 OFFSET 4;

    -- Nếu tìm thấy ngày chốt (tức là có ít nhất 5 ngày không ghim) thì mới tiến hành xóa
    IF cutoff_date IS NOT NULL THEN
        DELETE FROM `chat_dates`
        WHERE
            `is_pinned` = FALSE
            AND `chat_date` < cutoff_date;
    END IF;
END$$
-- Kết thúc định nghĩa Event bằng delimiter mới.

-- Trả lại delimiter mặc định là dấu chấm phẩy.
DELIMITER ;



