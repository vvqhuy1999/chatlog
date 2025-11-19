package com.example.chatlog.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lớp tiện ích để ghi log lỗi ra file theo thời gian
 */
public class LogUtils {
    
    private static final String LOG_DIRECTORY = "logs";
    private static final int MAX_LOG_FILES = 10;
    private static final ReentrantLock lock = new ReentrantLock();
    
    /**
     * Ghi log lỗi ra file với thông tin thời gian
     * 
     * @param serviceName Tên service gặp lỗi
     * @param errorMessage Thông báo lỗi
     * @param exception Exception gây ra lỗi (có thể null)
     */
    public static void logError(String serviceName, String errorMessage, Throwable exception) {
        LocalDateTime now = LocalDateTime.now();
        String logFileName = getLogFileName(now);
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        
        StringBuilder logEntry = new StringBuilder();
        logEntry.append("[").append(timestamp).append("] ");
        logEntry.append("[ERROR] ");
        logEntry.append("[").append(serviceName).append("] ");
        logEntry.append("❌ ").append(errorMessage);
        logEntry.append("\n");
        logEntry.append("════════════════════════════ ERROR DETAILS ════════════════════════════");
        
        if (exception != null) {
            logEntry.append("\n▶ Exception Type: ").append(exception.getClass().getName());
            logEntry.append("\n▶ Error Message: ").append(exception.getMessage());
            logEntry.append("\n▶ Root Cause: ").append(getRootCauseMessage(exception));
            logEntry.append("\n\n▶ Stack Trace:");
            
            // Thêm stack trace chi tiết
            for (StackTraceElement element : exception.getStackTrace()) {
                logEntry.append("\n    at ").append(element.toString());
            }
            
            // Thêm thông tin về cause nếu có
            Throwable cause = exception.getCause();
            if (cause != null) {
                logEntry.append("\n\n▶ Caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage());
                for (StackTraceElement element : cause.getStackTrace()) {
                    logEntry.append("\n    at ").append(element.toString());
                }
            }
        } else {
            logEntry.append("\n▶ No exception details available");
        }
        
        logEntry.append("\n════════════════════════════ END ERROR DETAILS ════════════════════════════");
        
        writeToFile(logFileName, logEntry.toString());
        
        // In ra console để debug
        System.err.println(logEntry.toString());
    }
    
    /**
     * Lấy thông điệp từ nguyên nhân gốc của exception
     */
    private static String getRootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getName() + ": " + rootCause.getMessage();
    }
    
    /**
     * Ghi log chi tiết về lỗi cụ thể với thông tin bối cảnh
     * 
     * @param serviceName Tên service gặp lỗi
     * @param errorMessage Thông báo lỗi
     * @param exception Exception gây ra lỗi
     * @param context Thông tin bối cảnh (các tham số, giá trị, trạng thái khi lỗi xảy ra)
     */
    public static void logDetailedError(String serviceName, String errorMessage, Throwable exception, Map<String, Object> context) {
        LocalDateTime now = LocalDateTime.now();
        String logFileName = getLogFileName(now);
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        
        StringBuilder logEntry = new StringBuilder();
        logEntry.append("[").append(timestamp).append("] ");
        logEntry.append("[ERROR_DETAILED] ");
        logEntry.append("[").append(serviceName).append("] ");
        logEntry.append("❌ ").append(errorMessage);
        logEntry.append("\n");
        logEntry.append("════════════════════════════ ERROR DETAILS ════════════════════════════");
        
        // Thông tin bối cảnh
        logEntry.append("\n▶ CONTEXT:");
        if (context != null && !context.isEmpty()) {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                logEntry.append("\n   - ").append(entry.getKey()).append(": ");
                if (entry.getValue() != null) {
                    String value = entry.getValue().toString();
                    // Giới hạn độ dài của giá trị để tránh log quá dài
                    if (value.length() > 1000) {
                        value = value.substring(0, 1000) + "... (truncated)";
                    }
                    logEntry.append(value);
                } else {
                    logEntry.append("null");
                }
            }
        } else {
            logEntry.append("\n   No context provided");
        }
        
        // Thông tin exception
        if (exception != null) {
            logEntry.append("\n\n▶ EXCEPTION:");
            logEntry.append("\n   Type: ").append(exception.getClass().getName());
            logEntry.append("\n   Message: ").append(exception.getMessage());
            logEntry.append("\n   Root Cause: ").append(getRootCauseMessage(exception));
            
            // Chi tiết stack trace
            logEntry.append("\n\n▶ STACK TRACE:");
            for (StackTraceElement element : exception.getStackTrace()) {
                logEntry.append("\n    at ").append(element.toString());
            }
            
            // Thông tin về cause
            Throwable cause = exception.getCause();
            if (cause != null) {
                logEntry.append("\n\n▶ CAUSED BY: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage());
                for (StackTraceElement element : cause.getStackTrace()) {
                    logEntry.append("\n    at ").append(element.toString());
                }
            }
            
            // Thông tin về suppressed exceptions
            Throwable[] suppressed = exception.getSuppressed();
            if (suppressed != null && suppressed.length > 0) {
                logEntry.append("\n\n▶ SUPPRESSED EXCEPTIONS:");
                for (Throwable t : suppressed) {
                    logEntry.append("\n   - ").append(t.getClass().getName()).append(": ").append(t.getMessage());
                }
            }
        } else {
            logEntry.append("\n\n▶ No exception details available");
        }
        
        // Thời gian xảy ra
        logEntry.append("\n\n▶ TIMESTAMP: ").append(now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        logEntry.append("\n════════════════════════════ END ERROR DETAILS ════════════════════════════");
        
        writeToFile(logFileName, logEntry.toString());
        
        // In ra console để debug
        System.err.println(logEntry.toString());
    }
    
    /**
     * Ghi log thông tin ra file với thông tin thời gian
     * 
     * @param serviceName Tên service
     * @param message Thông báo
     */
    public static void logInfo(String serviceName, String message) {
        LocalDateTime now = LocalDateTime.now();
        String logFileName = getLogFileName(now);
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        
        StringBuilder logEntry = new StringBuilder();
        logEntry.append("[").append(timestamp).append("] ");
        logEntry.append("[INFO] ");
        logEntry.append("[").append(serviceName).append("] ");
        logEntry.append(message);
        
        writeToFile(logFileName, logEntry.toString());
    }
    
    /**
     * Ghi log chi tiết về thành công với thông tin bối cảnh
     * 
     * @param serviceName Tên service thành công
     * @param successMessage Thông báo thành công
     * @param context Thông tin bối cảnh (các tham số, giá trị, trạng thái khi thành công)
     */
    public static void logDetailedSuccess(String serviceName, String successMessage, Map<String, Object> context) {
        LocalDateTime now = LocalDateTime.now();
        String logFileName = getLogFileName(now);
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        
        StringBuilder logEntry = new StringBuilder();
        logEntry.append("[").append(timestamp).append("] ");
        logEntry.append("[SUCCESS_DETAILED] ");
        logEntry.append("[").append(serviceName).append("] ");
        logEntry.append("✅ ").append(successMessage);
        logEntry.append("\n");
        logEntry.append("════════════════════════════ SUCCESS DETAILS ════════════════════════════");
        
        // Cập nhật phần CONTEXT để bỏ qua "esPreview" tránh trùng lặp với phần ES DATA PREVIEW riêng biệt

        logEntry.append("\n▶ CONTEXT:");
        if (context != null && !context.isEmpty()) {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                String key = entry.getKey();
                // Bỏ qua các field được xử lý riêng
                if ("esPreview".equals(key) || "dynamicExamples".equals(key) || 
                    "openaiEsData".equals(key) || "openrouterEsData".equals(key) ||
                    "openaiDslQuery".equals(key) || "openrouterDslQuery".equals(key)) {
                    continue;
                }
                logEntry.append("\n   - ").append(key).append(": ");
                if (entry.getValue() != null) {
                    String value = entry.getValue().toString();
                    // Giới hạn độ dài của giá trị để tránh log quá dài
                    if (value.length() > 1000) {
                        value = value.substring(0, 1000) + "... (truncated)";
                    }
                    logEntry.append(value);
                } else {
                    logEntry.append("null");
                }
            }
        } else {
            logEntry.append("\n   No context provided");
        }

        // Phần DSL QUERIES từ 2 AI
        logEntry.append("\n\n▶ DSL QUERIES:");
        String openaiDslQuery = context != null && context.get("openaiDslQuery") != null ? context.get("openaiDslQuery").toString() : null;
        String openrouterDslQuery = context != null && context.get("openrouterDslQuery") != null ? context.get("openrouterDslQuery").toString() : null;
        
        if (openaiDslQuery != null && !openaiDslQuery.equals("N/A")) {
            String query = openaiDslQuery;
            if (query.length() > 800) {
                query = query.substring(0, 800) + "... (truncated)";
            }
            logEntry.append("\n   OpenAI DSL: ").append(query.replace("\n", " "));
        } else {
            logEntry.append("\n   OpenAI DSL: N/A");
        }
        
        if (openrouterDslQuery != null && !openrouterDslQuery.equals("N/A")) {
            String query = openrouterDslQuery;
            if (query.length() > 800) {
                query = query.substring(0, 800) + "... (truncated)";
            }
            logEntry.append("\n   OpenRouter DSL: ").append(query.replace("\n", " "));
        } else {
            logEntry.append("\n   OpenRouter DSL: N/A");
        }

        // Hiển thị dữ liệu chi tiết theo từng nguồn nếu có (đã cắt ngắn)
        String openaiEsData = context != null && context.get("openaiEsData") != null ? context.get("openaiEsData").toString() : null;
        String openrouterEsData = context != null && context.get("openrouterEsData") != null ? context.get("openrouterEsData").toString() : null;
        if (openaiEsData != null || openrouterEsData != null) {
            logEntry.append("\n\n▶ ES DATA (FULL, truncated):");
            if (openaiEsData != null) {
                String s = openaiEsData;
                if (s.length() > 1500) s = s.substring(0, 1500) + "... (truncated)";
                logEntry.append("\n   OpenAI Data: ").append(s.replace("\n", " "));
            }
            if (openrouterEsData != null) {
                String s = openrouterEsData;
                if (s.length() > 1500) s = s.substring(0, 1500) + "... (truncated)";
                logEntry.append("\n   OpenRouter Data: ").append(s.replace("\n", " "));
            }
        }
        
        // Phần AI Results nếu có (từ openai/openrouter)
        logEntry.append("\n\n▶ AI RESULTS SUMMARY:");
        if (context != null) {
            Map<String, Object> aiSummary = (Map<String, Object>) context.get("aiSummary");
            if (aiSummary != null) {
                for (Map.Entry<String, Object> entry : aiSummary.entrySet()) {
                    logEntry.append("\n   - ").append(entry.getKey()).append(": ").append(entry.getValue());
                }
            } else {
                logEntry.append("\n   No AI summary available");
            }
        }
        
        // Phần DYNAMIC EXAMPLES (Vector Search Results) nếu có
        if (context != null) {
            String dynamicExamples = (String) context.get("dynamicExamples");
            if (dynamicExamples != null && !dynamicExamples.isEmpty()) {
                logEntry.append("\n\n▶ DYNAMIC EXAMPLES (Vector Search):");
                logEntry.append("\n   Total length: ").append(dynamicExamples.length()).append(" characters");
                // Đếm số examples (tìm pattern "Example " hoặc "Example:")
                int exampleCount = 0;
                String lowerExamples = dynamicExamples.toLowerCase();
                int index = 0;
                while ((index = lowerExamples.indexOf("example ", index)) != -1) {
                    exampleCount++;
                    index += "example ".length();
                }
                logEntry.append("\n   Number of examples found: ").append(exampleCount);
                // Hiển thị đầy đủ không truncate
                logEntry.append("\n").append(dynamicExamples.replace("\n", "\n   "));
            }
        }
        
        // Thời gian xảy ra
        logEntry.append("\n\n▶ TIMESTAMP: ").append(now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        logEntry.append("\n════════════════════════════ END SUCCESS DETAILS ════════════════════════════");
        
        writeToFile(logFileName, logEntry.toString());
        
        // In ra console để debug (optional cho success)
        System.out.println(logEntry.toString());
    }
    
    /**
     * Tạo tên file log dựa trên ngày hiện tại
     */
    private static String getLogFileName(LocalDateTime dateTime) {
        String datePart = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String hourPart = dateTime.format(DateTimeFormatter.ofPattern("HH"));
        return LOG_DIRECTORY + File.separator + "chatlog_" + datePart + "_" + hourPart + ".log";
    }
    
    /**
     * Ghi nội dung vào file log
     * Sử dụng lock để đảm bảo thread-safe khi nhiều thread cùng ghi log
     */
    private static void writeToFile(String fileName, String content) {
        lock.lock();
        try {
            // Đảm bảo thư mục logs tồn tại
            File directory = new File(LOG_DIRECTORY);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            // Ghi log vào file
            try (FileWriter fw = new FileWriter(fileName, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(content);
                pw.println(); // Thêm dòng trống để phân tách các log entries
            } catch (IOException e) {
                System.err.println("Không thể ghi log vào file: " + fileName);
                e.printStackTrace();
            }

            cleanupOldLogFiles();
        } finally {
            lock.unlock();
        }
    }

    private static void cleanupOldLogFiles() {
        File directory = new File(LOG_DIRECTORY);
        File[] logFiles = directory.listFiles((dir, name) -> name.startsWith("chatlog_") && name.endsWith(".log"));
        if (logFiles == null || logFiles.length <= MAX_LOG_FILES) {
            return;
        }

        Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified).reversed());

        for (int i = MAX_LOG_FILES; i < logFiles.length; i++) {
            try {
                if (!logFiles[i].delete()) {
                    System.err.println("Không thể xóa log cũ: " + logFiles[i].getName());
                }
            } catch (Exception e) {
                System.err.println("Lỗi khi xóa log cũ: " + logFiles[i].getName());
                e.printStackTrace();
            }
        }
    }
}
