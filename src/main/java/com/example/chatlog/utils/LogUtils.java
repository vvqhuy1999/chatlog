package com.example.chatlog.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        logEntry.append(errorMessage);
        
        if (exception != null) {
            logEntry.append("\nException: ").append(exception.getClass().getName());
            logEntry.append("\nMessage: ").append(exception.getMessage());
            logEntry.append("\nStackTrace: ");
            for (StackTraceElement element : exception.getStackTrace()) {
                logEntry.append("\n    at ").append(element.toString());
            }
        }
        
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
