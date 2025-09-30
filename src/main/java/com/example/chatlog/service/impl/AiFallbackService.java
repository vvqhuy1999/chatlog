package com.example.chatlog.service.impl;

import com.example.chatlog.utils.QueryTemplates;
import org.springframework.stereotype.Service;

/**
 * Service xử lý fallback queries và template selection
 * Bao gồm: chọn query mẫu phù hợp dựa trên intent của người dùng
 */
@Service
public class AiFallbackService {
    
    /**
     * Chọn query fallback phù hợp dựa trên nội dung tin nhắn của người dùng
     * @param userMessage Tin nhắn từ người dùng
     * @return Query string từ QueryTemplates
     */
    public String selectFallbackQuery(String userMessage) {
        String m = userMessage != null ? userMessage.toLowerCase() : "";
        
        // Heuristics: pick the closest template from QueryTemplates library (broader coverage)
        
        // 1) Port scan
        if (m.contains("quét port") || m.contains("port scan") || m.contains("port scanning")
            || m.contains("nhiều port khác nhau") || m.contains("cardinality") ) {
            return QueryTemplates.PORT_SCAN_BY_USER;
        }
        
        // 2) Brute force / failed logins
        if (m.contains("brute force") || m.contains("đăng nhập thất bại") || m.contains("login failure")
            || m.contains("nhiều lần thất bại") || m.contains("thử mật khẩu") ) {
            return QueryTemplates.BRUTE_FORCE_LOGIN_ATTEMPTS;
        }
        
        // 3) Data exfiltration / large uploads
        if (m.contains("exfiltration") || m.contains("tải lên quá nhiều") || m.contains("upload lớn")
            || m.contains("1gb") || m.contains("1 gb") || m.contains("large upload") ) {
            return QueryTemplates.LARGE_DATA_UPLOADS;
        }
        
        // 4) Outbound connections / Vietnam directions
        if (m.contains("outbound") || m.contains("ra ngoài") || m.contains("vietnam") || m.contains("việt nam") || m.contains("vn")) {
            return QueryTemplates.OUTBOUND_PORT_ANALYSIS;
        }
        
        // 4.1) Outbound connections from Vietnam (explicit)
        if (m.contains("outbound") && (m.contains("from vietnam") || m.contains("từ việt nam") || m.contains("từ vn"))) {
            return QueryTemplates.OUTBOUND_CONNECTIONS_FROM_VIETNAM;
        }
        
        // 5) Top blocked source IPs / blocked analysis
        if (m.contains("top ip bị chặn") || m.contains("top blocked") || m.contains("bị chặn nhiều nhất") ) {
            return QueryTemplates.TOP_BLOCKED_SOURCE_IPS;
        }
        
        // 6) High risk IPs sessions
        if (m.contains("rủi ro cao") || m.contains("high risk") ) {
            return QueryTemplates.HIGH_RISK_IPS_SESSIONS;
        }
        
        // 7) Top blocking rules
        if (m.contains("rule chặn nhiều nhất") || m.contains("top blocking rules") ) {
            return QueryTemplates.TOP_BLOCKING_RULES;
        }
        
        // 7.1) Top blocked source IPs
        if (m.contains("ip bị chặn nhiều nhất") || m.contains("top ip bị chặn") || m.contains("top blocked source ips")) {
            return QueryTemplates.TOP_BLOCKED_SOURCE_IPS;
        }
        
        // 8) RDP/SSH/ICMP specific traffic
        if (m.contains("rdp") || m.contains("3389")) {
            return QueryTemplates.RDP_TRAFFIC_FROM_WAN;
        }
        if (m.contains("ssh") || m.contains("22 ") || m.endsWith(" 22") ) {
            return QueryTemplates.BLOCKED_SSH_CONNECTIONS_BY_USER;
        }
        if (m.contains("icmp") || m.contains("ping") || m.contains("abnormal icmp")) {
            return QueryTemplates.ABNORMAL_ICMP_TRAFFIC;
        }
        
        // 8.1) Blocked ICMP by user
        if (m.contains("icmp") && m.contains("blocked")) {
            return QueryTemplates.BLOCKED_ICMP_BY_USER;
        }
        
        // 9) Admin logins / failed admin logins
        if (m.contains("admin login") || m.contains("đăng nhập admin") ) {
            if (m.contains("thất bại") || m.contains("failed")) {
                return QueryTemplates.FAILED_ADMIN_LOGINS;
            }
            return QueryTemplates.ADMIN_LOGINS;
        }
        
        // 9.1) Admin logins from foreign countries
        if ((m.contains("admin") && m.contains("login")) && (m.contains("nước ngoài") || m.contains("foreign") || m.contains("quốc gia khác"))) {
            return QueryTemplates.ADMIN_LOGINS_FROM_FOREIGN_COUNTRIES;
        }
        
        // 10) Config / rule changes
        if (m.contains("thay đổi cấu hình") || m.contains("configuration changes") ) {
            if (m.contains("ips") || m.contains("av") ) {
                return QueryTemplates.IPS_AV_CONFIGURATION_CHANGES;
            }
            if (m.contains("rule") ) {
                return QueryTemplates.FIREWALL_RULE_CHANGES;
            }
            if (m.contains("shaping") ) {
                return QueryTemplates.SHAPING_POLICY_CHANGES;
            }
            return QueryTemplates.CONFIGURATION_CHANGES_BY_USER;
        }
        
        // 11) WAN/Interface changes
        if (m.contains("wan interface") || m.contains("wan") && m.contains("interface")) {
            return QueryTemplates.WAN_INTERFACE_CHANGES;
        }
        
        // 12) Webfilter/P2P/FTP
        if (m.contains("webfilter") || m.contains("web filter")) {
            return QueryTemplates.TOP_USERS_BLOCKED_BY_WEBFILTER;
        }
        if (m.contains("p2p") || m.contains("torrent")) {
            return QueryTemplates.BLOCKED_P2P_TRAFFIC;
        }
        if (m.contains("ftp")) {
            return QueryTemplates.BLOCKED_FTP_CONNECTIONS;
        }
        
        // 13) Admin web access from LAN
        if (m.contains("admin web") && (m.contains("lan") || m.contains("from lan"))) {
            return QueryTemplates.BLOCKED_ADMIN_WEB_ACCESS_FROM_LAN;
        }
        
        // 14) Blocked RDP from LAN
        if (m.contains("rdp") && (m.contains("lan") || m.contains("from lan"))) {
            return QueryTemplates.BLOCKED_RDP_FROM_LAN;
        }
        
        // 15) Excessive admin port connections 15m
        if (m.contains("admin") && m.contains("port") && (m.contains("quá nhiều") || m.contains("excessive"))) {
            return QueryTemplates.EXCESSIVE_ADMIN_PORT_CONNECTIONS_QUERY;
        }
        
        // 16) Brute force detection generic
        if (m.contains("brute force detection")) {
            return QueryTemplates.BRUTE_FORCE_DETECTION;
        }
        
        // 17) Data exfiltration detection generic
        if (m.contains("data exfiltration") || m.contains("exfiltration detection")) {
            return QueryTemplates.DATA_EXFILTRATION_DETECTION;
        }
        
        // 18) Excessive admin port connections (generic)
        if (m.contains("excessive admin port connections")) {
            return QueryTemplates.EXCESSIVE_ADMIN_PORT_CONNECTIONS;
        }
        
        // 19) High risk IPs sessions (generic already above, keep redundancy safe)
        if (m.contains("high risk ips sessions")) {
            return QueryTemplates.HIGH_RISK_IPS_SESSIONS;
        }
        
        // Default: outbound analysis as a safe generic template
        return QueryTemplates.OUTBOUND_PORT_ANALYSIS;
    }
    
    /**
     * Lấy danh sách tất cả các query templates có sẵn
     * @return Map chứa tên và nội dung của các templates
     */
    public java.util.Map<String, String> getAllQueryTemplates() {
        java.util.Map<String, String> templates = new java.util.HashMap<>();
        
        templates.put("PORT_SCAN_BY_USER", QueryTemplates.PORT_SCAN_BY_USER);
        templates.put("BRUTE_FORCE_LOGIN_ATTEMPTS", QueryTemplates.BRUTE_FORCE_LOGIN_ATTEMPTS);
        templates.put("LARGE_DATA_UPLOADS", QueryTemplates.LARGE_DATA_UPLOADS);
        templates.put("OUTBOUND_PORT_ANALYSIS", QueryTemplates.OUTBOUND_PORT_ANALYSIS);
        templates.put("OUTBOUND_CONNECTIONS_FROM_VIETNAM", QueryTemplates.OUTBOUND_CONNECTIONS_FROM_VIETNAM);
        templates.put("TOP_BLOCKED_SOURCE_IPS", QueryTemplates.TOP_BLOCKED_SOURCE_IPS);
        templates.put("HIGH_RISK_IPS_SESSIONS", QueryTemplates.HIGH_RISK_IPS_SESSIONS);
        templates.put("TOP_BLOCKING_RULES", QueryTemplates.TOP_BLOCKING_RULES);
        templates.put("RDP_TRAFFIC_FROM_WAN", QueryTemplates.RDP_TRAFFIC_FROM_WAN);
        templates.put("BLOCKED_SSH_CONNECTIONS_BY_USER", QueryTemplates.BLOCKED_SSH_CONNECTIONS_BY_USER);
        templates.put("ABNORMAL_ICMP_TRAFFIC", QueryTemplates.ABNORMAL_ICMP_TRAFFIC);
        templates.put("BLOCKED_ICMP_BY_USER", QueryTemplates.BLOCKED_ICMP_BY_USER);
        templates.put("FAILED_ADMIN_LOGINS", QueryTemplates.FAILED_ADMIN_LOGINS);
        templates.put("ADMIN_LOGINS", QueryTemplates.ADMIN_LOGINS);
        templates.put("ADMIN_LOGINS_FROM_FOREIGN_COUNTRIES", QueryTemplates.ADMIN_LOGINS_FROM_FOREIGN_COUNTRIES);
        templates.put("IPS_AV_CONFIGURATION_CHANGES", QueryTemplates.IPS_AV_CONFIGURATION_CHANGES);
        templates.put("FIREWALL_RULE_CHANGES", QueryTemplates.FIREWALL_RULE_CHANGES);
        templates.put("SHAPING_POLICY_CHANGES", QueryTemplates.SHAPING_POLICY_CHANGES);
        templates.put("CONFIGURATION_CHANGES_BY_USER", QueryTemplates.CONFIGURATION_CHANGES_BY_USER);
        templates.put("WAN_INTERFACE_CHANGES", QueryTemplates.WAN_INTERFACE_CHANGES);
        templates.put("TOP_USERS_BLOCKED_BY_WEBFILTER", QueryTemplates.TOP_USERS_BLOCKED_BY_WEBFILTER);
        templates.put("BLOCKED_P2P_TRAFFIC", QueryTemplates.BLOCKED_P2P_TRAFFIC);
        templates.put("BLOCKED_FTP_CONNECTIONS", QueryTemplates.BLOCKED_FTP_CONNECTIONS);
        templates.put("BLOCKED_ADMIN_WEB_ACCESS_FROM_LAN", QueryTemplates.BLOCKED_ADMIN_WEB_ACCESS_FROM_LAN);
        templates.put("BLOCKED_RDP_FROM_LAN", QueryTemplates.BLOCKED_RDP_FROM_LAN);
        templates.put("EXCESSIVE_ADMIN_PORT_CONNECTIONS_QUERY", QueryTemplates.EXCESSIVE_ADMIN_PORT_CONNECTIONS_QUERY);
        templates.put("BRUTE_FORCE_DETECTION", QueryTemplates.BRUTE_FORCE_DETECTION);
        templates.put("DATA_EXFILTRATION_DETECTION", QueryTemplates.DATA_EXFILTRATION_DETECTION);
        templates.put("EXCESSIVE_ADMIN_PORT_CONNECTIONS", QueryTemplates.EXCESSIVE_ADMIN_PORT_CONNECTIONS);
        
        return templates;
    }
    
    /**
     * Tìm kiếm query template phù hợp dựa trên từ khóa
     * @param keyword Từ khóa tìm kiếm
     * @return Query string phù hợp hoặc null nếu không tìm thấy
     */
    public String searchQueryTemplate(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        
        String lowerKeyword = keyword.toLowerCase();
        java.util.Map<String, String> templates = getAllQueryTemplates();
        
        // Tìm kiếm theo tên template
        for (java.util.Map.Entry<String, String> entry : templates.entrySet()) {
            if (entry.getKey().toLowerCase().contains(lowerKeyword)) {
                return entry.getValue();
            }
        }
        
        // Tìm kiếm theo nội dung query
        for (java.util.Map.Entry<String, String> entry : templates.entrySet()) {
            if (entry.getValue().toLowerCase().contains(lowerKeyword)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * Lấy thống kê về các query templates
     * @return Map chứa thống kê
     */
    public java.util.Map<String, Object> getQueryTemplatesStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        java.util.Map<String, String> templates = getAllQueryTemplates();
        
        stats.put("total_templates", templates.size());
        stats.put("templates", templates.keySet());
        
        // Đếm số lượng templates theo loại
        int securityTemplates = 0;
        int networkTemplates = 0;
        int adminTemplates = 0;
        int configTemplates = 0;
        
        for (String templateName : templates.keySet()) {
            String lowerName = templateName.toLowerCase();
            if (lowerName.contains("brute") || lowerName.contains("blocked") || lowerName.contains("attack") || 
                lowerName.contains("risk") || lowerName.contains("security")) {
                securityTemplates++;
            } else if (lowerName.contains("network") || lowerName.contains("port") || lowerName.contains("traffic") || 
                      lowerName.contains("outbound") || lowerName.contains("rdp") || lowerName.contains("ssh")) {
                networkTemplates++;
            } else if (lowerName.contains("admin") || lowerName.contains("login")) {
                adminTemplates++;
            } else if (lowerName.contains("config") || lowerName.contains("rule") || lowerName.contains("change")) {
                configTemplates++;
            }
        }
        
        stats.put("security_templates", securityTemplates);
        stats.put("network_templates", networkTemplates);
        stats.put("admin_templates", adminTemplates);
        stats.put("config_templates", configTemplates);
        
        return stats;
    }
}
