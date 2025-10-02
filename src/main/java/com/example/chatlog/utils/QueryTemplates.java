package com.example.chatlog.utils;

import java.util.Map;
import java.util.HashMap;

/**
 * Lớp tiện ích chứa các template query Elasticsearch thường dùng
 * 
 * Lớp này cung cấp các template query đã được định nghĩa sẵn để tái sử dụng
 * và tùy chỉnh dễ dàng theo nhu cầu cụ thể.
 */
public class QueryTemplates {

    /**
     * Template query phát hiện quét port
     * Phát hiện người dùng kết nối đến nhiều port khác nhau trong khoảng thời gian ngắn
     */
    public static final String PORT_SCAN_DETECTION = """
            {
              "query": {
                "range": {
                  "@timestamp": {
                    "gte": "now-15m"
                  }
                }
              },
              "aggs": {
                "by_user": {
                  "terms": {
                    "field": "source.user.name",
                    "size": 20
                  },
                  "aggs": {
                    "unique_ports": {
                      "cardinality": {
                        "field": "destination.port"
                      }
                    },
                    "port_scan": {
                      "bucket_selector": {
                        "buckets_path": {
                          "p": "unique_ports"
                        },
                        "script": "params.p > 10"
                      }
                    }
                  }
                }
              },
              "size": 0
            }
            """;

    /**
     * Template query phân tích kết nối outbound theo port
     * Phân tích người dùng kết nối ra ngoài đến nhiều port khác nhau
     */
    public static final String OUTBOUND_PORT_ANALYSIS = """
            {
              "query": {
                "bool": {
                  "filter": [
                    {
                      "range": {
                        "@timestamp": {
                          "gte": "now-15m"
                        }
                      }
                    },
                    {
                      "terms": {
                        "network.direction": [
                          "outbound"
                        ]
                      }
                    }
                  ]
                }
              },
              "aggs": {
                "by_user": {
                  "terms": {
                    "field": "source.user.name",
                    "size": 50,
                    "order": {
                      "unique_ports": "desc"
                    }
                  },
                  "aggs": {
                    "unique_ports": {
                      "cardinality": {
                        "field": "destination.port"
                      }
                    }
                  }
                }
              },
              "size": 0
            }
            """;

    /**
     * Template query phát hiện brute force login
     * Phát hiện nhiều lần đăng nhập thất bại từ cùng một địa chỉ IP
     */
    public static final String BRUTE_FORCE_DETECTION = """
            {
              "query": {
                "bool": {
                  "must": [
                    {
                      "term": {
                        "event.action": "login"
                      }
                    },
                    {
                      "term": {
                        "event.outcome": "failure"
                      }
                    }
                  ],
                  "filter": [
                    {
                      "range": {
                        "@timestamp": {
                          "gte": "now-1h"
                        }
                      }
                    }
                  ]
                }
              },
              "aggs": {
                "by_ip": {
                  "terms": {
                    "field": "source.ip",
                    "size": 20
                  },
                  "aggs": {
                    "fail_count": {
                      "value_count": {
                        "field": "event.outcome"
                      }
                    },
                    "brute_force": {
                      "bucket_selector": {
                        "buckets_path": {
                          "c": "fail_count"
                        },
                        "script": "params.c > 10"
                      }
                    }
                  }
                }
              },
              "size": 0
            }
            """;

    /**
     * Template query phát hiện data exfiltration
     * Phát hiện người dùng upload lượng dữ liệu lớn ra bên ngoài
     */
    public static final String DATA_EXFILTRATION_DETECTION = """
            {
              "query": {
                "bool": {
                  "must": [
                    {
                      "term": {
                        "network.direction": "outbound"
                      }
                    }
                  ],
                  "filter": [
                    {
                      "range": {
                        "@timestamp": {
                          "gte": "now-1h"
                        }
                      }
                    }
                  ]
                }
              },
              "aggs": {
                "by_user": {
                  "terms": {
                    "field": "source.user.name",
                    "size": 20
                  },
                  "aggs": {
                    "bytes_sent": {
                      "sum": {
                        "field": "network.bytes"
                      }
                    },
                    "big_upload": {
                      "bucket_selector": {
                        "buckets_path": {
                          "b": "bytes_sent"
                        },
                        "script": "params.b > 1073741824"
                      }
                    }
                  }
                }
              },
              "size": 0
            }
            """;
            
    /**
     * Template query phát hiện kết nối đến admin port quá nhiều
     * Phát hiện IP kết nối đến các port quản trị (22, 443) quá nhiều lần
     */
    public static final String EXCESSIVE_ADMIN_PORT_CONNECTIONS = """
            {
              "query": {
                "bool": {
                  "filter": [
                    {
                      "terms": {
                        "destination.port": [
                          22,
                          443
                        ]
                      }
                    },
                    {
                      "range": {
                        "@timestamp": {
                          "gte": "now-15m"
                        }
                      }
                    }
                  ]
                }
              },
              "aggs": {
                "by_src": {
                  "terms": {
                    "field": "source.ip",
                    "size": 20
                  },
                  "aggs": {
                    "conn_count": {
                      "value_count": {
                        "field": "destination.port"
                      }
                    },
                    "suspicious": {
                      "bucket_selector": {
                        "buckets_path": {
                          "c": "conn_count"
                        },
                        "script": "params.c > 50"
                      }
                    }
                  }
                }
              },
              "size": 0
            }
            """;

    /**
     * Thay thế các placeholder trong query template
     * 
     * @param template Template query với các placeholder dạng {{key}}
     * @param params Map chứa các cặp key-value để thay thế
     * @return Query đã được thay thế các placeholder
     */
    public static String formatQuery(String template, Map<String, Object> params) {
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue().toString();
            
            // Nếu giá trị là số, không cần dấu ngoặc kép
            if (entry.getValue() instanceof Number) {
                result = result.replace("\"" + placeholder + "\"", value);
            }
            
            // Thay thế placeholder thông thường
            result = result.replace(placeholder, value);
        }
        return result;
    }
    
    /**
     * Tạo query phân tích kết nối outbound theo port với tham số tùy chỉnh
     * 
     * @param timeRange Khoảng thời gian (ví dụ: "now-15m", "now-1h")
     * @param size Số lượng kết quả trả về
     * @return Query đã được tùy chỉnh
     */
    public static String createOutboundPortAnalysis(String timeRange, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("timeRange", timeRange);
        params.put("size", size);
        
        String template = """
                {
                  "query": {
                    "bool": {
                      "filter": [
                        {
                          "range": {
                            "@timestamp": {
                              "gte": "{{timeRange}}"
                            }
                          }
                        },
                        {
                          "terms": {
                            "network.direction": [
                              "outbound"
                            ]
                          }
                        }
                      ]
                    }
                  },
                  "aggs": {
                    "by_user": {
                      "terms": {
                        "field": "source.user.name",
                        "size": {{size}},
                        "order": {
                          "unique_ports": "desc"
                        }
                      },
                      "aggs": {
                        "unique_ports": {
                          "cardinality": {
                            "field": "destination.port"
                          }
                        }
                      }
                    }
                  },
                  "size": 0
                }
                """;
        
        return formatQuery(template, params);
    }
    
    /**
     * Tạo query phát hiện quét port với tham số tùy chỉnh
     * 
     * @param timeRange Khoảng thời gian (ví dụ: "now-15m", "now-1h")
     * @param threshold Ngưỡng số lượng port để coi là quét port
     * @return Query đã được tùy chỉnh
     */
    public static String createPortScanDetection(String timeRange, int threshold) {
        Map<String, Object> params = new HashMap<>();
        params.put("timeRange", timeRange);
        params.put("threshold", threshold);
        
        String template = """
                {
                  "query": {
                    "range": {
                      "@timestamp": {
                        "gte": "{{timeRange}}"
                      }
                    }
                  },
                  "aggs": {
                    "by_user": {
                      "terms": {
                        "field": "source.user.name",
                        "size": 20
                      },
                      "aggs": {
                        "unique_ports": {
                          "cardinality": {
                            "field": "destination.port"
                          }
                        },
                        "port_scan": {
                          "bucket_selector": {
                            "buckets_path": {
                              "p": "unique_ports"
                            },
                            "script": "params.p > {{threshold}}"
                          }
                        }
                      }
                    }
                  },
                  "size": 0
                }
                """;
        
        return formatQuery(template, params);
    }
    
    /* ===== FORTIGATE QUERY TEMPLATES FROM FULL COLLECTION ===== */
    
    /**
     * 1) Trong 24 giờ qua, những IP nguồn nào bị chặn (deny) nhiều nhất?
     */
    public static final String TOP_BLOCKED_SOURCE_IPS = """
            {
              "size": 0,
              "query": {
                "bool": {
                  "filter": [
                    { "term": { "fortinet.firewall.action": "deny" } },
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              },
              "aggs": {
                "top_blocked_sources": {
                  "terms": { "field": "source.ip", "size": 50 }
                }
              }
            }
            """;
    
    /**
     * 2) Từ IP nguồn cụ thể, đích nào nhận nhiều bytes nhất trong 24 giờ qua?
     */
    public static String getTopDestinationsByBytesFromSource(String sourceIp) {
        Map<String, Object> params = new HashMap<>();
        params.put("sourceIp", sourceIp);
        
        String template = """
                {
                  "size": 0,
                  "query": {
                    "bool": {
                      "filter": [
                        { "term": { "source.ip": "{{sourceIp}}" } },
                        { "range": { "@timestamp": { "gte": "now-24h" } } }
                      ]
                    }
                  },
                  "aggs": {
                    "by_dst": {
                      "terms": { "field": "destination.ip", "size": 50, "order": { "bytes_sum": "desc" } },
                      "aggs": {
                        "bytes_sum": { "sum": { "field": "network.bytes" } }
                      }
                    }
                  }
                }
                """;
        
        return formatQuery(template, params);
    }
    
    /**
     * 3) Liệt kê các phiên có mức rủi ro IPS cao (crlevel = high/critical) trong 1 ngày qua
     */
    public static final String HIGH_RISK_IPS_SESSIONS = """
            {
              "size": 50,
              "sort": [ { "@timestamp": "desc" } ],
              "query": {
                "bool": {
                  "filter": [
                    { "terms": { "fortinet.firewall.crlevel": ["high", "critical"] } },
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 4) Trong 7 ngày qua, các kết nối outbound từ Việt Nam ra nước ngoài (không phải Việt Nam) là gì?
     */
    public static final String OUTBOUND_CONNECTIONS_FROM_VIETNAM = """
            {
              "size": 100,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "network.direction": "outbound" } },
                    { "term": { "source.geo.country_name": "Vietnam" } }
                  ],
                  "must_not": [
                    { "term": { "destination.geo.country_name": "Vietnam" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-7d" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 5) Có những lần đăng nhập thất bại của người dùng cụ thể trong 48 giờ qua không?
     */
    public static String getFailedLoginsByUser(String username) {
        Map<String, Object> params = new HashMap<>();
        params.put("username", username);
        
        String template = """
                {
                  "size": 100,
                  "sort": [ { "@timestamp": "desc" } ],
                  "query": {
                    "bool": {
                      "must": [
                        { "term": { "source.user.name": "{{username}}" } },
                        { "term": { "event.action": "login" } },
                        { "term": { "event.outcome": "failure" } }
                      ],
                      "filter": [
                        { "range": { "@timestamp": { "gte": "now-48h" } } }
                      ]
                    }
                  }
                }
                """;
        
        return formatQuery(template, params);
    }
    
    /**
     * 6) Những rule nào chặn nhiều nhất trong 24 giờ qua?
     */
    public static final String TOP_BLOCKING_RULES = """
            {
              "size": 50,
              "query": {
                "bool": {
                  "filter": [
                    { "term": { "fortinet.firewall.action": "deny" } },
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              },
              "aggs": {
                "rules": {
                  "terms": { "field": "rule.name", "size": 50 }
                }
              }
            }
            """;
    
    /**
     * 7) Trong 1 giờ qua, có lưu lượng RDP (port 3389) đi vào từ WAN không?
     */
    public static final String RDP_TRAFFIC_FROM_WAN = """
            {
              "size": 100,
              "sort": [ { "@timestamp": "desc" } ],
              "query": {
                "bool": {
                  "filter": [
                    { "term": { "destination.port": 3389 } },
                    { "term": { "fortinet.firewall.srcintfrole": "wan" } },
                    { "range": { "@timestamp": { "gte": "now-1h" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 8) Các phiên DNAT tới server nội bộ trong 24 giờ gần đây là gì?
     */
    public static String getDnatSessionsToInternalServer(String destinationIp) {
        Map<String, Object> params = new HashMap<>();
        params.put("destinationIp", destinationIp);
        
        String template = """
                {
                  "size": 100,
                  "sort": [ { "@timestamp": "desc" } ],
                  "query": {
                    "bool": {
                      "filter": [
                        { "term": { "fortinet.firewall.trandisp": "dnat" } },
                        { "term": { "destination.ip": "{{destinationIp}}" } },
                        { "range": { "@timestamp": { "gte": "now-24h" } } }
                      ]
                    }
                  }
                }
                """;
        
        return formatQuery(template, params);
    }
    
    /**
     * 9) Trong 1 giờ qua, IP nguồn nào gửi nhiều gói ICMP bất thường (> 10.000 packets)?
     */
    public static final String ABNORMAL_ICMP_TRAFFIC = """
            {
              "size": 0,
              "query": {
                "bool": {
                  "filter": [
                    { "term": { "network.protocol": "icmp" } },
                    { "range": { "@timestamp": { "gte": "now-1h" } } }
                  ]
                }
              },
              "aggs": {
                "by_source": {
                  "terms": { "field": "source.ip", "size": 50 },
                  "aggs": {
                    "pkt_sum": { "sum": { "field": "network.packets" } },
                    "heavy_senders": {
                      "bucket_selector": {
                        "buckets_path": { "p": "pkt_sum" },
                        "script": "params.p > 10000"
                      }
                    }
                  }
                }
              }
            }
            """;
    
    /**
     * 10) Có traffic bị 'drop' bởi policy shaping tên cụ thể trên thiết bị cụ thể trong 24 giờ qua không?
     */
    public static String getDroppedTrafficByShapingPolicy(String deviceName, String policyName) {
        Map<String, Object> params = new HashMap<>();
        params.put("deviceName", deviceName);
        params.put("policyName", policyName);
        
        String template = """
                {
                  "size": 100,
                  "sort": [ { "@timestamp": "desc" } ],
                  "query": {
                    "bool": {
                      "must": [
                        { "term": { "observer.name": "{{deviceName}}" } },
                        { "term": { "fortinet.firewall.shapingpolicyname": "{{policyName}}" } }
                      ],
                      "filter": [
                        { "term": { "fortinet.firewall.action": "deny" } },
                        { "range": { "@timestamp": { "gte": "now-24h" } } }
                      ]
                    }
                  }
                }
                """;
        
        return formatQuery(template, params);
    }
    
    /**
     * 11) Ai đã đăng nhập vào thiết bị Fortigate bằng tài khoản admin trong 24 giờ qua?
     */
    public static final String ADMIN_LOGINS = """
            {
              "size": 50,
              "sort": [{ "@timestamp": "desc" }],
              "query": {
                "bool": {
                  "must": [
                    { "term": { "source.user.name": "admin" } },
                    { "term": { "event.action": "login" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 12) Có phiên đăng nhập quản trị thất bại (admin login failed) nào trong 1 giờ qua không?
     */
    public static final String FAILED_ADMIN_LOGINS = """
            {
              "size": 50,
              "sort": [{ "@timestamp": "desc" }],
              "query": {
                "bool": {
                  "must": [
                    { "term": { "event.action": "login" } },
                    { "term": { "event.outcome": "failure" } },
                    { "term": { "source.user.name": "admin" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-1h" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 13) Trong 7 ngày qua có thay đổi nào về rule firewall không?
     */
    public static final String FIREWALL_RULE_CHANGES = """
            {
              "size": 100,
              "sort": [{ "@timestamp": "desc" }],
              "query": {
                "bool": {
                  "must": [
                    { "term": { "event.type": "configuration" } },
                    { "term": { "rule.ruleset": "firewall" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-7d" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 14) Ai đã thay đổi cấu hình hệ thống trong 24 giờ qua?
     */
    public static final String CONFIGURATION_CHANGES_BY_USER = """
            {
              "size": 100,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "event.type": "configuration" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              },
              "aggs": {
                "by_user": {
                  "terms": { "field": "source.user.name", "size": 50 }
                }
              }
            }
            """;
    
    /**
     * 15) Có đăng nhập quản trị nào từ quốc gia khác Việt Nam trong 48 giờ qua?
     */
    public static final String ADMIN_LOGINS_FROM_FOREIGN_COUNTRIES = """
            {
              "size": 50,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "event.action": "login" } },
                    { "term": { "fortinet.firewall.srcintfrole": "wan" } }
                  ],
                  "must_not": [
                    { "term": { "source.geo.country_name": "Vietnam" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-48h" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 16) Trong 24 giờ qua có ai bật/tắt tính năng IPS hoặc AV không?
     */
    public static final String IPS_AV_CONFIGURATION_CHANGES = """
            {
              "size": 50,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "event.type": "configuration" } },
                    { "terms": { "rule.category": ["IPS", "Antivirus"] } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 17) Có dấu hiệu brute-force login (quá nhiều login failure từ 1 IP) trong 1 giờ qua không?
     */
    public static final String BRUTE_FORCE_LOGIN_ATTEMPTS = """
            {
              "size": 0,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "event.action": "login" } },
                    { "term": { "event.outcome": "failure" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-1h" } } }
                  ]
                }
              },
              "aggs": {
                "by_ip": {
                  "terms": { "field": "source.ip", "size": 20 },
                  "aggs": {
                    "fail_count": { "value_count": { "field": "event.outcome" } },
                    "brute_force": {
                      "bucket_selector": {
                        "buckets_path": { "c": "fail_count" },
                        "script": "params.c > 10"
                      }
                    }
                  }
                }
              }
            }
            """;
    
    /**
     * 18) Có IP nào thực hiện quá nhiều kết nối đến port quản trị (443, 22) trong 15 phút gần đây không?
     */
    public static final String EXCESSIVE_ADMIN_PORT_CONNECTIONS_QUERY = """
            {
              "size": 0,
              "query": {
                "bool": {
                  "filter": [
                    { "terms": { "destination.port": [22, 443] } },
                    { "range": { "@timestamp": { "gte": "now-15m" } } }
                  ]
                }
              },
              "aggs": {
                "by_src": {
                  "terms": { "field": "source.ip", "size": 20 },
                  "aggs": {
                    "conn_count": { "value_count": { "field": "destination.port" } },
                    "suspicious": {
                      "bucket_selector": {
                        "buckets_path": { "c": "conn_count" },
                        "script": "params.c > 50"
                      }
                    }
                  }
                }
              }
            }
            """;
    
    /**
     * 19) Ai đã thay đổi policy shaping trong 7 ngày qua?
     */
    public static final String SHAPING_POLICY_CHANGES = """
            {
              "size": 100,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "event.type": "configuration" } },
                    { "exists": { "field": "fortinet.firewall.shapingpolicyname" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-7d" } } }
                  ]
                }
              },
              "aggs": {
                "by_user": {
                  "terms": { "field": "source.user.name", "size": 50 }
                }
              }
            }
            """;
    
    /**
     * 20) Có thay đổi nào trong cấu hình interface WAN trong 24 giờ qua không?
     */
    public static final String WAN_INTERFACE_CHANGES = """
            {
              "size": 50,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "event.type": "configuration" } }
                  ],
                  "should": [
                    { "term": { "observer.ingress.interface.name": "wan" } },
                    { "term": { "observer.egress.interface.name": "wan" } }
                  ],
                  "minimum_should_match": 1,
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 21) Trong 24 giờ qua, những user nào bị chặn khi cố gắng kết nối SSH?
     */
    public static final String BLOCKED_SSH_CONNECTIONS_BY_USER = """
            {
              "size": 0,
              "query": {
                "bool": {
                  "filter": [
                    { "term": { "destination.port": 22 } },
                    { "term": { "fortinet.firewall.action": "deny" } },
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              },
              "aggs": {
                "blocked_users": {
                  "terms": { "field": "source.user.name", "size": 50 }
                }
              }
            }
            """;
    
    /**
     * 22) Có user nào bị chặn khi truy cập RDP (3389) từ LAN ra ngoài internet không?
     */
    public static final String BLOCKED_RDP_FROM_LAN = """
            {
              "size": 50,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "destination.port": 3389 } },
                    { "term": { "fortinet.firewall.action": "deny" } },
                    { "term": { "fortinet.firewall.srcintfrole": "lan" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 23) Trong 1 giờ qua có user nào gửi nhiều gói ICMP bị chặn?
     */
    public static final String BLOCKED_ICMP_BY_USER = """
            {
              "size": 0,
              "query": {
                "bool": {
                  "filter": [
                    { "term": { "network.protocol": "icmp" } },
                    { "term": { "fortinet.firewall.action": "deny" } },
                    { "range": { "@timestamp": { "gte": "now-1h" } } }
                  ]
                }
              },
              "aggs": {
                "blocked_icmp": {
                  "terms": { "field": "source.user.name", "size": 50 },
                  "aggs": {
                    "pkt_sum": { "sum": { "field": "network.packets" } }
                  }
                }
              }
            }
            """;
    
    /**
     * 24) Những user nào cố gắng quét port (nhiều destination.port khác nhau) trong 15 phút gần đây?
     */
    public static final String PORT_SCAN_BY_USER = """
            {
              "size": 0,
              "query": {
                "bool": {
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-15m" } } }
                  ]
                }
              },
              "aggs": {
                "by_user": {
                  "terms": { "field": "source.user.name", "size": 20 },
                  "aggs": {
                    "unique_ports": { "cardinality": { "field": "destination.port" } },
                    "port_scan": {
                      "bucket_selector": {
                        "buckets_path": { "p": "unique_ports" },
                        "script": "params.p > 10"
                      }
                    }
                  }
                }
              }
            }
            """;
    
    /**
     * 25) Có user nào bị chặn khi tải file qua FTP (port 21) không?
     */
    public static final String BLOCKED_FTP_CONNECTIONS = """
            {
              "size": 50,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "destination.port": 21 } },
                    { "term": { "fortinet.firewall.action": "deny" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 26) Trong 7 ngày qua, user nào bị chặn nhiều nhất vì truy cập web không hợp lệ?
     */
    public static final String TOP_USERS_BLOCKED_BY_WEBFILTER = """
            {
              "size": 0,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "fortinet.firewall.subtype": "webfilter" } },
                    { "term": { "fortinet.firewall.action": "deny" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-7d" } } }
                  ]
                }
              },
              "aggs": {
                "top_users": {
                  "terms": { "field": "source.user.name", "size": 50 }
                }
              }
            }
            """;
    
    /**
     * 27) Có user nào kết nối đến quốc gia bị hạn chế trong 24 giờ qua không?
     */
    public static String getConnectionsToRestrictedCountries(String[] restrictedCountries) {
        Map<String, Object> params = new HashMap<>();
        params.put("countries", String.join("\", \"", restrictedCountries));
        
        String template = """
                {
                  "size": 50,
                  "query": {
                    "bool": {
                      "must": [
                        { "terms": { "destination.geo.country_name": ["{{countries}}"] } }
                      ],
                      "filter": [
                        { "range": { "@timestamp": { "gte": "now-24h" } } }
                      ]
                    }
                  }
                }
                """;
        
        return formatQuery(template, params);
    }
    
    /**
     * 28) Trong 1 giờ qua có user nào upload quá nhiều dữ liệu ra ngoài (>1GB) không?
     */
    public static final String LARGE_DATA_UPLOADS = """
            {
              "size": 0,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "network.direction": "outbound" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-1h" } } }
                  ]
                }
              },
              "aggs": {
                "by_user": {
                  "terms": { "field": "source.user.name", "size": 20 },
                  "aggs": {
                    "bytes_sent": { "sum": { "field": "network.bytes" } },
                    "big_upload": {
                      "bucket_selector": {
                        "buckets_path": { "b": "bytes_sent" },
                        "script": "params.b > 1073741824"
                      }
                    }
                  }
                }
              }
            }
            """;
    
    /**
     * 29) Có user nào trong LAN cố gắng truy cập dịch vụ quản trị web của Fortigate (port 443) bị chặn?
     */
    public static final String BLOCKED_ADMIN_WEB_ACCESS_FROM_LAN = """
            {
              "size": 50,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "destination.port": 443 } },
                    { "term": { "fortinet.firewall.action": "deny" } },
                    { "term": { "fortinet.firewall.srcintfrole": "lan" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              }
            }
            """;
    
    /**
     * 30) Trong 24 giờ qua, có user nào bị chặn khi dùng ứng dụng P2P (torrent) không?
     */
    public static final String BLOCKED_P2P_TRAFFIC = """
            {
              "size": 50,
              "query": {
                "bool": {
                  "must": [
                    { "term": { "rule.category": "p2p" } },
                    { "term": { "fortinet.firewall.action": "deny" } }
                  ],
                  "filter": [
                    { "range": { "@timestamp": { "gte": "now-24h" } } }
                  ]
                }
              }
            }
            """;

    /**
     * Botnet detection query template
     * CRITICAL: Uses "exists" query, NOT "term" with "true" value
     */
    public static final String BOTNET_DETECTION = """
            {
              "query": {
                "bool": {
                  "filter": [
                    {
                      "exists": {
                        "field": "fortinet.firewall.botnetip"
                      }
                    },
                    {
                      "range": {
                        "@timestamp": {
                          "gte": "now-24h"
                        }
                      }
                    }
                  ]
                }
              },
              "_source": [
                "@timestamp",
                "source.ip",
                "destination.ip",
                "fortinet.firewall.botnetip",
                "fortinet.firewall.botnetdomain",
                "rule.name",
                "action"
              ],
              "sort": [
                {
                  "@timestamp": "desc"
                }
              ],
              "size": 50
            }
            """;
}
