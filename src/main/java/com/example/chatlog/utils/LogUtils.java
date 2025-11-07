package com.example.chatlog.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lớp tiện ích để ghi log lỗi ra file theo thời gian
 */
public class LogUtils {
    
    private static final String LOG_DIRECTORY = "logs";
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
                // Bỏ qua esPreview vì nó được xử lý riêng ở phần ES DATA PREVIEW
                if ("esPreview".equals(key)) {
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

        // Phần ES DATA PREVIEW giữ nguyên nhưng đảm bảo labels rõ ràng (OpenAI ES và OpenRouter ES)
        logEntry.append("\n\n▶ ES DATA PREVIEW:");
        String esPreview = (String) context.get("esPreview");
        if (esPreview != null && !esPreview.isEmpty()) {
            if (esPreview.length() > 1000) {
                esPreview = esPreview.substring(0, 1000) + "... (preview truncated)";
            }
            logEntry.append("\n   " + esPreview.replace("\n", "\n   "));
        } else {
            logEntry.append("\n   No ES data available (OpenAI ES: N/A | OpenRouter ES: N/A)");
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
        
        // Phần HYBRID SCORE DEBUG nếu có
        if (context != null) {
            String hybridScoreDebug = (String) context.get("hybridScoreDebug");
            if (hybridScoreDebug != null && !hybridScoreDebug.isEmpty()) {
                logEntry.append("\n\n▶ HYBRID SCORE DEBUG:");
                logEntry.append("\n").append(hybridScoreDebug.replace("\n", "\n   "));
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
        } finally {
            lock.unlock();
        }
    }
}
