package com.example.chatlog.utils;

import java.util.List;

public class SchemaHint {

  /**
   * Schema hint chính cho Fortinet integration theo ECS fields
   * Tương đương với SCHEMA_HINT trong Python
   */
  public static String getSchemaHint() {
    return """
        Index pattern: {index}.
        Use ECS field names typical for Fortinet integration:
        - @timestamp (date)
        - source.user.name (keyword)
        - source.user.roles (keyword, e.g., "Administrator")
        
        IMPORTANT ROLE MAPPINGS:
        - Questions about "admin", "ad", "administrator" should use "Administrator" (capitalized)
        - Always normalize roles: admin/ad/administrator → Administrator
        - Example query: {"term": {"source.user.roles": "Administrator"}}
        
        - source.ip (ip)
        - destination.ip (ip)
        - destination.as.organization.name (keyword, external organization name, e.g., "Google LLC", "Amazon.com", "Microsoft Corporation")
        - destination.as.number (long, ASN number)
        - event.action (keyword, e.g., "login", "logout", "accept", "deny", "close", "server-rst", "client-rst", "dns", "timeout", "ssl-anomaly", "logged-on", "signature", "logged-off", "ssh_login", "Health Check")
        - event.outcome (keyword: success/failure)
        - event.module (should be "fortinet")
        - event.dataset (should be "fortinet_fortigate.log")
        - message (text)
        - destination.port (long)
        - rule.name (keyword, e.g., "TO_INTERNET_SDWAN", "AD_SERVICES", "BLOCK_EXTERNAL_DNS", "AP_CONTROLLER", "SNMP_SERVICE", "TO_AP_CONTROLLER", "PRINT_CONTROLLER_CONNECT", "TO_VMS_CAMERA", "ADMIN_MGMT", "HTTP_HTTPs_SERVICES", "TO_JBSIGN")
        - log.level (keyword, e.g., "info", "error", "information", "warning", "notice", "alert", "warn", "critical")
        - http.request.method (keyword, e.g., "GET", "POST")
        - user_agent.original (text)
        - host.name (keyword)
        - process.name (keyword)
        - process.pid (long)
        
        Default time filter: @timestamp >= NOW() - {hours} HOURS unless the question specifies otherwise.
        When counting or grouping, return meaningful columns (e.g., user.name, source.user.roles, source.ip, count, last_seen).
        IMPORTANT: Only add event.action/event.outcome filters when explicitly mentioned in the question.
        """;
  }

  /**
   * Chuẩn hóa roles thành format chuẩn
   * Ví dụ: admin, ad, Admin, administrator -> Administrator
   */
  public static String normalizeRole(String role) {
    if (role == null || role.trim().isEmpty()) {
      return role;
    }

    String normalized = role.trim().toLowerCase();

    // Chuẩn hóa các biến thể của Administrator
    switch (normalized) {
      case "admin":
      case "ad":
      case "administrator":
        return "Administrator";
      default:
        // Giữ nguyên format gốc nhưng chuẩn hóa chữ hoa đầu
        return role.trim().substring(0, 1).toUpperCase() +
            role.trim().substring(1).toLowerCase();
    }
  }

  /**
   * Trả về danh sách schema hints (chỉ có một schema duy nhất)
   */
  public static List<String> allSchemas() {
    return List.of(getSchemaHint());
  }

  /**
   * Trả về role normalization rules để sử dụng trong AI prompt
   */
  public static String getRoleNormalizationRules() {
    return """
        ROLE NORMALIZATION RULES:
        - "admin", "ad", "administrator" → ALWAYS use "Administrator" (capitalized)
        - For source.user.roles field, normalize to standard format: "Administrator"
        - Example: {"term": {"source.user.roles": "Administrator"}} not "admin"
        """;
  }

  /**
   * Trả về example query cho admin roles
   */
  public static String getAdminRoleExample() {
    return """
        Question: "hôm ngày 11-09 có roles admin nào vào hệ thống hay ko?"
        Response: {"body":"{\\"query\\":{\\"bool\\":{\\"must\\":[{\\"term\\":{\\"source.user.roles\\":\\"Administrator\\"}},{\\"range\\":{\\"@timestamp\\":{\\"gte\\":\\"2025-09-11T00:00:00.000+07:00\\",\\"lte\\":\\"2025-09-11T23:59:59.999+07:00\\"}}}]}},\\"size\\":10}","query":1}
        """;
  }
}