package com.example.chatlog.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    // Tài khoản mặc định
    private static final String DEFAULT_USERNAME = "tanhpt";
    private static final String DEFAULT_PASSWORD = "admin123";
    
    // Theo dõi trạng thái đăng nhập đơn giản
    private static boolean isLoggedIn = false;
    private static String currentUser = null;

    // Đăng nhập
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // Kiểm tra tài khoản mật khẩu
            if (DEFAULT_USERNAME.equals(loginRequest.getUsername()) && 
                DEFAULT_PASSWORD.equals(loginRequest.getPassword())) {
                
                // Đăng nhập thành công
                isLoggedIn = true;
                currentUser = loginRequest.getUsername();
                response.put("success", true);
                response.put("message", "Đăng nhập thành công");
                response.put("username", loginRequest.getUsername());
        
                
                return ResponseEntity.ok(response);
            } else {
                // Đăng nhập thất bại
                response.put("success", false);
                response.put("message", "Tài khoản hoặc mật khẩu không đúng");
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi hệ thống");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Kiểm tra trạng thái đăng nhập bằng username
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkAuth(@RequestParam(value = "username", required = false) String username) {
        Map<String, Object> response = new HashMap<>();
        
        if (isLoggedIn && DEFAULT_USERNAME.equals(username) && DEFAULT_USERNAME.equals(currentUser)) {
            response.put("authenticated", true);
            response.put("message", "Đã đăng nhập");
            response.put("username", username);
        } else {
            response.put("authenticated", false);
            response.put("message", "Chưa đăng nhập hoặc username không hợp lệ");
        }
        
        return ResponseEntity.ok(response);
    }

    // Đăng xuất
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        // Reset trạng thái đăng nhập
        isLoggedIn = false;
        currentUser = null;
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Đăng xuất thành công");
        
        return ResponseEntity.ok(response);
    }



    // DTO class cho login request
    public static class LoginRequest {
        private String username;
        private String password;

        // Constructors
        public LoginRequest() {}

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }

        // Getters and setters
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
