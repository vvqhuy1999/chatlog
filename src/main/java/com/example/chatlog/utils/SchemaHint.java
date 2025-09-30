package com.example.chatlog.utils;

import java.util.List;

public class SchemaHint {

  /**
   * Schema hint chính cho Fortinet integration theo ECS fields
   * Tương đương với SCHEMA_HINT trong Python
   */
  /**
   * Schema hint chính cho Fortinet integration theo ECS fields
   * Tương đương với SCHEMA_HINT trong Python
   */

  public static String getSchemaHint() {
    return """
      // Index pattern: {index}.
      Based on the 8 ECS field categories for Fortinet integration, here are the available fields:
      
      === 1. APPLICATION / URL / DNS / HTTP / TLS ===
      - dns (object, DNS-related data)
      - dns.id (keyword, DNS transaction ID)
      - dns.question (object, DNS question section)
      - dns.question.class (keyword, DNS question class)
      - dns.question.name (keyword, DNS query name)
      - dns.question.registered_domain (keyword, registered domain from DNS query)
      - dns.question.subdomain (keyword, subdomain from DNS query)
      - dns.question.top_level_domain (keyword, top level domain from DNS query)
      - dns.question.type (keyword, DNS query type: A, AAAA, MX, etc.)
      - dns.resolved_ip (ip, resolved IP address from DNS response)
      - http (object, HTTP-related data)
      - http.request (object, HTTP request data)
      - http.request.method (keyword, e.g., "GET", "POST")
      - http.request.referrer (keyword, HTTP referrer header)
      - tls (object, TLS/SSL-related data)
      - tls.cipher (keyword, TLS cipher suite)
      - tls.client (object, TLS client certificate data)
      - tls.client.issuer (keyword, TLS client certificate issuer)
      - tls.client.server_name (keyword, TLS client server name)
      - tls.client.x509 (object, TLS client X.509 certificate data)
      - tls.client.x509.issuer (keyword, TLS client X.509 certificate issuer)
      - tls.client.x509.issuer.common_name (keyword, TLS client cert issuer common name)
      - tls.client.x509.public_key_algorithm (keyword, TLS client cert public key algorithm)
      - tls.curve (keyword, TLS elliptic curve)
      - tls.established (boolean, whether TLS connection was established)
      - tls.server (object, TLS server certificate data)
      - tls.server.hash (object, TLS server certificate hash)
      - tls.server.hash.sha1 (keyword, TLS server certificate SHA1 hash)
      - tls.server.issuer (keyword, TLS server certificate issuer)
      - tls.server.not_after (date, TLS server certificate expiration date)
      - tls.server.not_before (date, TLS server certificate valid from date)
      - tls.server.x509 (object, TLS server X.509 certificate data)
      - tls.server.x509.alternative_names (keyword, TLS server cert alternative names)
      - tls.server.x509.issuer (keyword, TLS server X.509 certificate issuer)
      - tls.server.x509.issuer.common_name (keyword, TLS server cert issuer common name)
      - tls.server.x509.not_after (date, TLS server cert expiration date)
      - tls.server.x509.not_before (date, TLS server cert valid from date)
      - tls.server.x509.public_key_algorithm (keyword, TLS server cert public key algorithm)
      - tls.server.x509.public_key_size (long, TLS server cert public key size in bits)
      - tls.server.x509.serial_number (keyword, TLS server cert serial number)
      - tls.server.x509.subject (keyword, TLS server cert subject)
      - tls.server.x509.subject.common_name (keyword, TLS server cert subject common name)
      - tls.version (keyword, TLS version: 1.2, 1.3)
      - tls.version_protocol (keyword, TLS version protocol)
      - url (object, URL-related data)
      - url.domain (keyword, domain name from URL)
      - url.extension (keyword, file extension from URL)
      - url.original (keyword, complete original URL)
      - url.original.text (text, complete original URL as text field)
      - url.path (keyword, URL path)
      - url.query (keyword, URL query string)
      - url.scheme (keyword, URL scheme: http, https, ftp, etc.)
      
      === 2. DEVICE / HOST / CONTAINER / CLOUD ===
      - cloud (object, cloud-related data)
      - cloud.account (object, cloud account information)
      - cloud.account.id (keyword, cloud account identifier)
      - cloud.availability_zone (keyword, cloud availability zone)
      - cloud.image (object, cloud image information)
      - cloud.image.id (keyword, cloud image identifier)
      - cloud.instance (object, cloud instance information)
      - cloud.instance.id (keyword, cloud instance identifier)
      - cloud.instance.name (keyword, cloud instance name)
      - cloud.machine (object, cloud machine information)
      - cloud.machine.type (keyword, cloud machine type)
      - cloud.project (object, cloud project information)
      - cloud.project.id (keyword, cloud project identifier)
      - cloud.provider (keyword, cloud provider: AWS, Azure, GCP)
      - cloud.region (keyword, cloud region)
      - container (object, container-related data)
      - container.id (keyword, container identifier)
      - container.image (object, container image information)
      - container.image.name (keyword, container image name)
      - container.name (keyword, container name)
      - host (object, host-related data)
      - host.architecture (keyword, system architecture: x86_64, arm64, etc.)
      - host.containerized (boolean, whether host is containerized)
      - host.domain (keyword, host domain name)
      - host.hostname (keyword, host hostname)
      - host.id (keyword, unique host identifier)
      - host.ip (ip, host IP address)
      - host.mac (keyword, host MAC address)
      - host.name (keyword, host name)
      - host.os (object, operating system information)
      - host.os.build (keyword, OS build number)
      - host.os.codename (keyword, OS codename)
      - host.os.family (keyword, OS family: windows, linux, macos)
      - host.os.kernel (keyword, OS kernel version)
      - host.os.name (keyword, operating system name)
      - host.os.name.text (text, operating system name as text field)
      - host.os.platform (keyword, OS platform)
      - host.os.version (keyword, operating system version)
      - host.type (keyword, host type: physical, virtual, container)
      - observer (object, network observer/device information)
      - observer.egress (object, egress interface information)
      - observer.egress.interface (object, egress interface details)
      - observer.egress.interface.name (keyword, egress interface name)
      - observer.ingress (object, ingress interface information)
      - observer.ingress.interface (object, ingress interface details)
      - observer.ingress.interface.name (keyword, ingress interface name)
      - observer.name (keyword, Fortigate device name)
      - observer.product (keyword, observer product name)
      - observer.serial_number (keyword, Fortigate serial number)
      - observer.type (keyword, observer type: firewall, router, switch)
      - observer.vendor (keyword, observer vendor: Fortinet, Cisco, etc.)
      

      === 3. FORTINET / FIREWALL METADATA ===
      - fortinet.firewall.acct_stat (keyword, accounting status)
      - fortinet.firewall.acktime (long, acknowledgment time)
      - fortinet.firewall.action (keyword, firewall action: allow/deny)
      - fortinet.firewall.activity (keyword, activity type)
      - fortinet.firewall.addr (keyword, address)
      - fortinet.firewall.addr_type (keyword, address type)
      - fortinet.firewall.addrgrp (keyword, address group)
      - fortinet.firewall.adgroup (keyword, AD group)
      - fortinet.firewall.admin (keyword, administrator name)
      - fortinet.firewall.advpnsc (keyword, ADVPN service)
      - fortinet.firewall.age (long, age in seconds)
      - fortinet.firewall.agent (keyword, agent name)
      - fortinet.firewall.alarmid (keyword, alarm identifier)
      - fortinet.firewall.analyticscksum (keyword, analytics checksum)
      - fortinet.firewall.analyticssubmit (keyword, analytics submission)
      - fortinet.firewall.ap (keyword, access point)
      - fortinet.firewall.app-type (keyword, application type)
      - fortinet.firewall.appact (keyword, application action)
      - fortinet.firewall.appid (keyword, application ID)
      - fortinet.firewall.applist (keyword, application list)
      - fortinet.firewall.apprisk (keyword, application risk)
      - fortinet.firewall.apscan (keyword, AP scan)
      - fortinet.firewall.apsn (keyword, AP serial number)
      - fortinet.firewall.apstatus (keyword, AP status)
      - fortinet.firewall.aptype (keyword, AP type)
      - fortinet.firewall.assigned (keyword, assigned value)
      - fortinet.firewall.assignip (ip, assigned IP address)
      - fortinet.firewall.attachment (keyword, attachment name)
      - fortinet.firewall.auditid (keyword, audit ID)
      - fortinet.firewall.auditreporttype (keyword, audit report type)
      - fortinet.firewall.auditscore (long, audit score)
      - fortinet.firewall.audittime (date, audit time)
      - fortinet.firewall.authgrp (keyword, authentication group)
      - fortinet.firewall.authid (keyword, authentication ID)
      - fortinet.firewall.authmsg (keyword, authentication message)
      - fortinet.firewall.authproto (keyword, authentication protocol)
      - fortinet.firewall.authserver (keyword, authentication server)
      - fortinet.firewall.bandwidth (long, bandwidth in bytes)
      - fortinet.firewall.banned_rule (keyword, banned rule)
      - fortinet.firewall.banned_src (ip, banned source IP)
      - fortinet.firewall.banword (keyword, banned word)
      - fortinet.firewall.bibandwidth (long, bidirectional bandwidth)
      - fortinet.firewall.botnetdomain (keyword, botnet domain)
      - fortinet.firewall.botnetip (ip, botnet IP address)
      - fortinet.firewall.bssid (keyword, BSSID)
      - fortinet.firewall.call_id (keyword, call ID)
      - fortinet.firewall.carrier (keyword, carrier name)
      - fortinet.firewall.carrier_ep (keyword, carrier endpoint)
      - fortinet.firewall.cat (keyword, category)
      - fortinet.firewall.category (keyword, detailed category)
      - fortinet.firewall.cc (keyword, country code)
      - fortinet.firewall.cdrcontent (keyword, CDR content)
      - fortinet.firewall.centralnatid (keyword, central NAT ID)
      - fortinet.firewall.cert (keyword, certificate)
      - fortinet.firewall.cert-type (keyword, certificate type)
      - fortinet.firewall.certhash (keyword, certificate hash)
      - fortinet.firewall.cfgattr (keyword, configuration attribute)
      - fortinet.firewall.cfgobj (keyword, configuration object)
      - fortinet.firewall.cfgpath (keyword, configuration path)
      - fortinet.firewall.cfgtid (keyword, configuration TID)
      - fortinet.firewall.cfgtxpower (long, configuration TX power)
      - fortinet.firewall.channel (long, wireless channel)
      - fortinet.firewall.channeltype (keyword, channel type)
      - fortinet.firewall.chassisid (keyword, chassis ID)
      - fortinet.firewall.checkname (keyword, check name)
      - fortinet.firewall.checksum (keyword, checksum)
      - fortinet.firewall.chgheaders (keyword, changed headers)
      - fortinet.firewall.cldobjid (keyword, cloud object ID)
      - fortinet.firewall.client_addr (ip, client address)
      - fortinet.firewall.cloudaction (keyword, cloud action)
      - fortinet.firewall.clouduser (keyword, cloud user)
      - fortinet.firewall.clustername (keyword, cluster name)
      - fortinet.firewall.column (keyword, column name)
      - fortinet.firewall.command (keyword, command executed)
      - fortinet.firewall.community (keyword, SNMP community)
      - fortinet.firewall.configcountry (keyword, configuration country)
      - fortinet.firewall.connection_type (keyword, connection type)
      - fortinet.firewall.conserve (keyword, conserve mode)
      - fortinet.firewall.constraint (keyword, constraint)
      - fortinet.firewall.contentdisarmed (keyword, content disarmed)
      - fortinet.firewall.contenttype (keyword, content type)
      - fortinet.firewall.cookies (keyword, cookies)
      - fortinet.firewall.count (long, count value)
      - fortinet.firewall.countapp (long, application count)
      - fortinet.firewall.countav (long, antivirus count)
      - fortinet.firewall.countcifs (long, CIFS count)
      - fortinet.firewall.countdlp (long, DLP count)
      - fortinet.firewall.countdns (long, DNS count)
      - fortinet.firewall.countemail (long, email count)
      - fortinet.firewall.countff (long, file filter count)
      - fortinet.firewall.countips (long, IPS count)
      - fortinet.firewall.countssh (long, SSH count)
      - fortinet.firewall.countssl (long, SSL count)
      - fortinet.firewall.countwaf (long, WAF count)
      - fortinet.firewall.countweb (long, web count)
      - fortinet.firewall.cpu (long, CPU usage percentage)
      - fortinet.firewall.craction (keyword, CR action)
      - fortinet.firewall.criticalcount (long, critical event count)
      - fortinet.firewall.crl (keyword, certificate revocation list)
      - fortinet.firewall.crlevel (keyword, IPS risk level: "low", "medium", "high", "critical")
      - fortinet.firewall.crscore (long, IPS risk score)
      - fortinet.firewall.cveid (keyword, CVE ID)
      - fortinet.firewall.daemon (keyword, daemon name)
      - fortinet.firewall.datarange (keyword, data range)
      - fortinet.firewall.date (date, event date)
      - fortinet.firewall.ddnsserver (keyword, DDNS server)
      - fortinet.firewall.deltabytes (long, delta bytes)
      - fortinet.firewall.desc (text, description)
      - fortinet.firewall.detectionmethod (keyword, detection method)
      - fortinet.firewall.devcategory (keyword, device category)
      - fortinet.firewall.devintfname (keyword, device interface name)
      - fortinet.firewall.devtype (keyword, endpoint device type)
      - fortinet.firewall.dhcp_msg (keyword, DHCP message)
      - fortinet.firewall.dintf (keyword, destination interface)
      - fortinet.firewall.disk (long, disk usage percentage)
      - fortinet.firewall.disklograte (long, disk log rate)
      - fortinet.firewall.dlpextra (keyword, DLP extra info)
      - fortinet.firewall.docsource (keyword, document source)
      - fortinet.firewall.domainctrlauthstate (keyword, domain control auth state)
      - fortinet.firewall.domainctrlauthtype (keyword, domain control auth type)
      - fortinet.firewall.domainctrldomain (keyword, domain control domain)
      - fortinet.firewall.domainctrlip (ip, domain control IP)
      - fortinet.firewall.domainctrlname (keyword, domain control name)
      - fortinet.firewall.domainctrlprotocoltype (keyword, domain control protocol type)
      - fortinet.firewall.domainctrlusername (keyword, domain control username)
      - fortinet.firewall.domainfilteridx (keyword, domain filter index)
      - fortinet.firewall.domainfilterlist (keyword, domain filter list)
      - fortinet.firewall.ds (keyword, DS field)
      - fortinet.firewall.dst_int (keyword, destination interface)
      - fortinet.firewall.dstcountry (keyword, destination country)
      - fortinet.firewall.dstdevcategory (keyword, destination device category)
      - fortinet.firewall.dstdevtype (keyword, destination device type)
      - fortinet.firewall.dstfamily (keyword, destination family)
      - fortinet.firewall.dsthwvendor (keyword, destination hardware vendor)
      - fortinet.firewall.dsthwversion (keyword, destination hardware version)
      - fortinet.firewall.dstinetsvc (keyword, destination internet service)
      - fortinet.firewall.dstintfrole (keyword, destination interface role: lan/wan/dmz)
      - fortinet.firewall.dstosname (keyword, destination OS name)
      - fortinet.firewall.dstosversion (keyword, destination OS version)
      - fortinet.firewall.dstserver (keyword, destination server)
      - fortinet.firewall.dstssid (keyword, destination SSID)
      - fortinet.firewall.dstswversion (keyword, destination software version)
      - fortinet.firewall.dstunauthusersource (keyword, destination unauthorized user source)
      - fortinet.firewall.dstuuid (keyword, destination UUID)
      - fortinet.firewall.duid (keyword, DUID)
      - fortinet.firewall.durationdelta (long, duration delta)
      - fortinet.firewall.eapolcnt (long, EAPOL count)
      - fortinet.firewall.eapoltype (keyword, EAPOL type)
      - fortinet.firewall.encrypt (keyword, encryption)
      - fortinet.firewall.encryption (keyword, encryption type)
      - fortinet.firewall.epoch (long, epoch time)
      - fortinet.firewall.espauth (keyword, ESP authentication)
      - fortinet.firewall.esptransform (keyword, ESP transform)
      - fortinet.firewall.exch (keyword, exchange)
      - fortinet.firewall.exchange (keyword, exchange type)
      - fortinet.firewall.expectedsignature (keyword, expected signature)
      - fortinet.firewall.expiry (date, expiry date)
      - fortinet.firewall.extrainfo (keyword, extra information)
      - fortinet.firewall.fams_pause (keyword, FAMS pause)
      - fortinet.firewall.fazlograte (long, FAZ log rate)
      - fortinet.firewall.fctemssn (keyword, FCT EMS SSN)
      - fortinet.firewall.fctuid (keyword, FCT UID)
      - fortinet.firewall.field (keyword, field name)
      - fortinet.firewall.filefilter (keyword, file filter)
      - fortinet.firewall.filehashsrc (keyword, file hash source)
      - fortinet.firewall.filtercat (keyword, filter category)
      - fortinet.firewall.filteridx (keyword, filter index)
      - fortinet.firewall.filtername (keyword, filter name)
      - fortinet.firewall.filtertype (keyword, filter type)
      - fortinet.firewall.fortiguardresp (keyword, FortiGuard response)
      - fortinet.firewall.forwardedfor (ip, forwarded for IP)
      - fortinet.firewall.fqdn (keyword, FQDN)
      - fortinet.firewall.frametype (keyword, frame type)
      - fortinet.firewall.freediskstorage (long, free disk storage)
      - fortinet.firewall.from (keyword, from field)
      - fortinet.firewall.from_vcluster (keyword, from vcluster)
      - fortinet.firewall.fsaverdict (keyword, FSA verdict)
      - fortinet.firewall.fwserver_name (keyword, firewall server name)
      - fortinet.firewall.gateway (ip, gateway IP)
      - fortinet.firewall.green (keyword, green status)
      - fortinet.firewall.groupid (keyword, group ID)
      - fortinet.firewall.ha-prio (long, HA priority)
      - fortinet.firewall.ha_group (keyword, HA group)
      - fortinet.firewall.ha_role (keyword, HA role)
      - fortinet.firewall.handshake (keyword, handshake)
      - fortinet.firewall.hash (keyword, hash value)
      - fortinet.firewall.hbdn_reason (keyword, HBDN reason)
      - fortinet.firewall.healthcheck (keyword, health check)
      - fortinet.firewall.highcount (long, high count)
      - fortinet.firewall.host (keyword, host)
      - fortinet.firewall.iaid (keyword, IAID)
      - fortinet.firewall.iccid (keyword, ICCID)
      - fortinet.firewall.icmpcode (long, ICMP code)
      - fortinet.firewall.icmpid (keyword, ICMP ID)
      - fortinet.firewall.icmptype (keyword, ICMP type)
      - fortinet.firewall.identifier (keyword, identifier)
      - fortinet.firewall.imei (keyword, IMEI)
      - fortinet.firewall.imsi (keyword, IMSI)
      - fortinet.firewall.in_spi (keyword, incoming SPI)
      - fortinet.firewall.inbandwidth (long, inbound bandwidth)
      - fortinet.firewall.incidentserialno (keyword, incident serial number)
      - fortinet.firewall.infected (keyword, infected status)
      - fortinet.firewall.infectedfilelevel (keyword, infected file level)
      - fortinet.firewall.informationsource (keyword, information source)
      - fortinet.firewall.init (keyword, initialization)
      - fortinet.firewall.initiator (keyword, initiator)
      - fortinet.firewall.interface (keyword, interface)
      - fortinet.firewall.intf (keyword, interface name)
      - fortinet.firewall.invalidmac (keyword, invalid MAC)
      - fortinet.firewall.ip (ip, IP address)
      - fortinet.firewall.iptype (keyword, IP type)
      - fortinet.firewall.jitter (long, jitter)
      - fortinet.firewall.keyword (keyword, keyword)
      - fortinet.firewall.kind (keyword, kind)
      - fortinet.firewall.kxproto (keyword, key exchange protocol)
      - fortinet.firewall.lanin (long, LAN in)
      - fortinet.firewall.lanout (long, LAN out)
      - fortinet.firewall.latency (long, latency)
      - fortinet.firewall.lease (long, lease time)
      - fortinet.firewall.license_limit (keyword, license limit)
      - fortinet.firewall.limit (long, limit value)
      - fortinet.firewall.line (keyword, line)
      - fortinet.firewall.live (keyword, live status)
      - fortinet.firewall.local (keyword, local)
      - fortinet.firewall.log (keyword, log)
      - fortinet.firewall.login (keyword, login)
      - fortinet.firewall.logsrc (keyword, log source)
      - fortinet.firewall.lowcount (long, low count)
      - fortinet.firewall.mac (keyword, MAC address)
      - fortinet.firewall.malform_data (keyword, malformed data)
      - fortinet.firewall.malform_desc (text, malformed description)
      - fortinet.firewall.manuf (keyword, manufacturer)
      - fortinet.firewall.masterdstmac (keyword, master destination MAC)
      - fortinet.firewall.mastersrcmac (keyword, master source MAC)
      - fortinet.firewall.mediumcount (long, medium count)
      - fortinet.firewall.mem (long, memory usage percentage)
      - fortinet.firewall.meshmode (keyword, mesh mode)
      - fortinet.firewall.message_type (keyword, message type)
      - fortinet.firewall.method (keyword, method)
      - fortinet.firewall.metric (long, metric)
      - fortinet.firewall.mgmtcnt (long, management count)
      - fortinet.firewall.mitm (keyword, MITM)
      - fortinet.firewall.mode (keyword, mode)
      - fortinet.firewall.module (keyword, module)
      - fortinet.firewall.monitor-name (keyword, monitor name)
      - fortinet.firewall.monitor-type (keyword, monitor type)
      - fortinet.firewall.mpsk (keyword, MPSK)
      - fortinet.firewall.msgproto (keyword, message protocol)
      - fortinet.firewall.mtu (long, MTU)
      - fortinet.firewall.name (keyword, name)
      - fortinet.firewall.nat (keyword, NAT)
      - fortinet.firewall.netid (keyword, network ID)
      - fortinet.firewall.new_status (keyword, new status)
      - fortinet.firewall.new_value (keyword, new value)
      - fortinet.firewall.newchannel (long, new channel)
      - fortinet.firewall.newchassisid (keyword, new chassis ID)
      - fortinet.firewall.newslot (keyword, new slot)
      - fortinet.firewall.newvalue (keyword, new value)
      - fortinet.firewall.nextstat (keyword, next status)
      - fortinet.firewall.nf_type (keyword, NF type)
      - fortinet.firewall.noise (long, noise level)
      - fortinet.firewall.old_status (keyword, old status)
      - fortinet.firewall.old_value (keyword, old value)
      - fortinet.firewall.oldchannel (long, old channel)
      - fortinet.firewall.oldchassisid (keyword, old chassis ID)
      - fortinet.firewall.oldslot (keyword, old slot)
      - fortinet.firewall.oldsn (keyword, old serial number)
      - fortinet.firewall.oldvalue (keyword, old value)
      - fortinet.firewall.oldwprof (keyword, old wireless profile)
      - fortinet.firewall.onwire (keyword, on wire)
      - fortinet.firewall.opercountry (keyword, operational country)
      - fortinet.firewall.opertxpower (long, operational TX power)
      - fortinet.firewall.osname (keyword, endpoint OS name)
      - fortinet.firewall.osversion (keyword, OS version)
      - fortinet.firewall.out_spi (keyword, outgoing SPI)
      - fortinet.firewall.outbandwidth (long, outbound bandwidth)
      - fortinet.firewall.outintf (keyword, outbound interface)
      - fortinet.firewall.packetloss (long, packet loss)
      - fortinet.firewall.passedcount (long, passed count)
      - fortinet.firewall.passwd (keyword, password)
      - fortinet.firewall.path (keyword, path)
      - fortinet.firewall.pdstport (long, destination port)
      - fortinet.firewall.peer (keyword, peer)
      - fortinet.firewall.peer_notif (keyword, peer notification)
      - fortinet.firewall.phase2_name (keyword, phase 2 name)
      - fortinet.firewall.phone (keyword, phone)
      - fortinet.firewall.phonenumber (keyword, phone number)
      - fortinet.firewall.pid (long, process ID)
      - fortinet.firewall.plan (keyword, plan)
      - fortinet.firewall.policyid (keyword, policy ID number)
      - fortinet.firewall.policytype (keyword, policy type)
      - fortinet.firewall.poluuid (keyword, policy UUID)
      - fortinet.firewall.poolname (keyword, pool name)
      - fortinet.firewall.port (long, port number)
      - fortinet.firewall.portbegin (long, port range begin)
      - fortinet.firewall.portend (long, port range end)
      - fortinet.firewall.probeproto (keyword, probe protocol)
      - fortinet.firewall.process (keyword, process)
      - fortinet.firewall.processtime (long, process time)
      - fortinet.firewall.profile (keyword, profile)
      - fortinet.firewall.profile_vd (keyword, profile VD)
      - fortinet.firewall.profilegroup (keyword, profile group)
      - fortinet.firewall.profiletype (keyword, profile type)
      - fortinet.firewall.psrcport (long, source port)
      - fortinet.firewall.qtypeval (keyword, query type value)
      - fortinet.firewall.quarskip (keyword, quarantine skip)
      - fortinet.firewall.quotaexceeded (keyword, quota exceeded)
      - fortinet.firewall.quotamax (long, quota maximum)
      - fortinet.firewall.quotatype (keyword, quota type)
      - fortinet.firewall.quotaused (long, quota used)
      - fortinet.firewall.radioband (keyword, radio band)
      - fortinet.firewall.radioid (keyword, radio ID)
      - fortinet.firewall.radioidclosest (keyword, closest radio ID)
      - fortinet.firewall.radioiddetected (keyword, detected radio ID)
      - fortinet.firewall.rate (long, rate)
      - fortinet.firewall.rawdata (keyword, raw data)
      - fortinet.firewall.rawdataid (keyword, raw data ID)
      - fortinet.firewall.rcvddelta (long, received delta)
      - fortinet.firewall.rcvdpktdelta (long, received packet delta)
      - fortinet.firewall.reason (keyword, reason)
      - fortinet.firewall.received (long, received bytes)
      - fortinet.firewall.receivedsignature (keyword, received signature)
      - fortinet.firewall.red (keyword, red status)
      - fortinet.firewall.referralurl (keyword, referral URL)
      - fortinet.firewall.remote (keyword, remote)
      - fortinet.firewall.remotewtptime (long, remote WTP time)
      - fortinet.firewall.replysrcintf (keyword, reply source interface)
      - fortinet.firewall.reporttype (keyword, report type)
      - fortinet.firewall.reqtype (keyword, request type)
      - fortinet.firewall.request_name (keyword, request name)
      - fortinet.firewall.result (keyword, result)
      - fortinet.firewall.role (keyword, role)
      - fortinet.firewall.rsrp (long, RSRP)
      - fortinet.firewall.rsrq (long, RSRQ)
      - fortinet.firewall.rssi (long, RSSI)
      - fortinet.firewall.rsso_key (keyword, RSSO key)
      - fortinet.firewall.ruledata (keyword, rule data)
      - fortinet.firewall.ruleid (keyword, rule ID number)
      - fortinet.firewall.ruletype (keyword, rule type)
      - fortinet.firewall.scanned (long, scanned count)
      - fortinet.firewall.scantime (long, scan time)
      - fortinet.firewall.scope (keyword, scope)
      - fortinet.firewall.security (keyword, security)
      - fortinet.firewall.sensitivity (keyword, sensitivity)
      - fortinet.firewall.sensor (keyword, sensor)
      - fortinet.firewall.sentdelta (long, sent delta)
      - fortinet.firewall.sentpktdelta (long, sent packet delta)
      - fortinet.firewall.seq (long, sequence number)
      - fortinet.firewall.serial (keyword, serial)
      - fortinet.firewall.serialno (keyword, serial number)
      - fortinet.firewall.server (keyword, server)
      - fortinet.firewall.session_id (keyword, session ID)
      - fortinet.firewall.sessionid (keyword, session ID)
      - fortinet.firewall.setuprate (long, setup rate)
      - fortinet.firewall.severity (keyword, severity)
      - fortinet.firewall.shaperdroprcvdbyte (long, shaper drop received bytes)
      - fortinet.firewall.shaperdropsentbyte (long, shaper drop sent bytes)
      - fortinet.firewall.shaperperipdropbyte (long, shaper per IP drop bytes)
      - fortinet.firewall.shaperperipname (keyword, shaper per IP name)
      - fortinet.firewall.shaperrcvdname (keyword, shaper received name)
      - fortinet.firewall.shapersentname (keyword, shaper sent name)
      - fortinet.firewall.shapingpolicyid (keyword, shaping policy ID)
      - fortinet.firewall.shapingpolicyname (keyword, shaping policy name)
      - fortinet.firewall.signal (long, signal strength)
      - fortinet.firewall.signalstrength (long, signal strength)
      - fortinet.firewall.sinr (long, SINR)
      - fortinet.firewall.size (long, size)
      - fortinet.firewall.ski (keyword, SKI)
      - fortinet.firewall.slamap (keyword, SLA map)
      - fortinet.firewall.slatargetid (keyword, SLA target ID)
      - fortinet.firewall.slot (keyword, slot)
      - fortinet.firewall.sn (keyword, serial number)
      - fortinet.firewall.snclosest (keyword, closest serial number)
      - fortinet.firewall.sndetected (keyword, detected serial number)
      - fortinet.firewall.snmeshparent (keyword, mesh parent serial number)
      - fortinet.firewall.spi (keyword, SPI)
      - fortinet.firewall.src_int (keyword, source interface)
      - fortinet.firewall.srccountry (keyword, source country)
      - fortinet.firewall.srcfamily (keyword, source family)
      - fortinet.firewall.srchwvendor (keyword, source hardware vendor)
      - fortinet.firewall.srchwversion (keyword, source hardware version)
      - fortinet.firewall.srcinetsvc (keyword, source internet service)
      - fortinet.firewall.srcintfrole (keyword, source interface role: lan/wan/dmz)
      - fortinet.firewall.srcname (keyword, source name)
      - fortinet.firewall.srcserver (keyword, source server)
      - fortinet.firewall.srcssid (keyword, source SSID)
      - fortinet.firewall.srcswversion (keyword, source software version)
      - fortinet.firewall.srcuuid (keyword, source UUID)
      - fortinet.firewall.sscname (keyword, SSC name)
      - fortinet.firewall.ssid (keyword, SSID)
      - fortinet.firewall.sslaction (keyword, SSL action)
      - fortinet.firewall.ssllocal (keyword, SSL local)
      - fortinet.firewall.sslremote (keyword, SSL remote)
      - fortinet.firewall.stacount (long, station count)
      - fortinet.firewall.stage (keyword, stage)
      - fortinet.firewall.stamac (keyword, station MAC)
      - fortinet.firewall.state (keyword, state)
      - fortinet.firewall.status (keyword, status)
      - fortinet.firewall.stitch (keyword, stitch)
      - fortinet.firewall.stitchaction (keyword, stitch action)
      - fortinet.firewall.subject (keyword, subject)
      - fortinet.firewall.submodule (keyword, submodule)
      - fortinet.firewall.subservice (keyword, subservice)
      - fortinet.firewall.subtype (keyword, log subtype: webfilter/forward/virus)
      - fortinet.firewall.switchproto (keyword, switch protocol)
      - fortinet.firewall.sync_status (keyword, sync status)
      - fortinet.firewall.sync_type (keyword, sync type)
      - fortinet.firewall.sysuptime (long, system uptime)
      - fortinet.firewall.tamac (keyword, TA MAC)
      - fortinet.firewall.temperature (long, temperature)
      - fortinet.firewall.time (date, time)
      - fortinet.firewall.timestamp (date, timestamp)
      - fortinet.firewall.to (keyword, to field)
      - fortinet.firewall.to_vcluster (keyword, to vcluster)
      - fortinet.firewall.total (long, total)
      - fortinet.firewall.totalsession (long, total sessions)
      - fortinet.firewall.trace_id (keyword, trace ID)
      - fortinet.firewall.trandisp (keyword, NAT translation type: snat/dnat/noop)
      - fortinet.firewall.tranip (ip, translation IP)
      - fortinet.firewall.transid (keyword, transaction ID)
      - fortinet.firewall.transip (ip, translation IP)
      - fortinet.firewall.translationid (keyword, translation ID)
      - fortinet.firewall.trigger (keyword, trigger)
      - fortinet.firewall.trueclntip (ip, true client IP)
      - fortinet.firewall.tunnelid (keyword, tunnel ID)
      - fortinet.firewall.tunnelip (ip, tunnel IP)
      - fortinet.firewall.tunneltype (keyword, tunnel type)
      - fortinet.firewall.type (keyword, log type: traffic/event/utm)
      - fortinet.firewall.ui (keyword, UI)
      - fortinet.firewall.unauthusersource (keyword, unauthorized user source)
      - fortinet.firewall.unit (keyword, unit)
      - fortinet.firewall.urlfilteridx (keyword, URL filter index)
      - fortinet.firewall.urlfilterlist (keyword, URL filter list)
      - fortinet.firewall.urlsource (keyword, URL source)
      - fortinet.firewall.urltype (keyword, URL type)
      - fortinet.firewall.used (long, used)
      - fortinet.firewall.used_for_type (keyword, used for type)
      - fortinet.firewall.utmaction (keyword, UTM action)
      - fortinet.firewall.utmref (keyword, UTM reference)
      - fortinet.firewall.uuid (keyword, UUID)
      - fortinet.firewall.valid (keyword, valid)
      - fortinet.firewall.vap (keyword, VAP)
      - fortinet.firewall.vapmode (keyword, VAP mode)
      - fortinet.firewall.vcluster (keyword, vcluster)
      - fortinet.firewall.vcluster_member (keyword, vcluster member)
      - fortinet.firewall.vcluster_state (keyword, vcluster state)
      - fortinet.firewall.vd (keyword, VDOM name)
      - fortinet.firewall.vdname (keyword, VDOM name)
      - fortinet.firewall.vendorurl (keyword, vendor URL)
      - fortinet.firewall.version (keyword, version)
      - fortinet.firewall.vip (keyword, VIP)
      - fortinet.firewall.voip_proto (keyword, VoIP protocol)
      - fortinet.firewall.vpn (keyword, VPN)
      - fortinet.firewall.vpntunnel (keyword, VPN tunnel)
      - fortinet.firewall.vpntype (keyword, VPN type)
      - fortinet.firewall.vrf (keyword, VRF)
      - fortinet.firewall.vwlid (keyword, VWL ID)
      - fortinet.firewall.vwlquality (keyword, VWL quality)
      - fortinet.firewall.vwlservice (keyword, VWL service)
      - fortinet.firewall.vwpvlanid (keyword, VWP VLAN ID)
      - fortinet.firewall.wanin (long, WAN in)
      - fortinet.firewall.waninfo (keyword, WAN info)
      - fortinet.firewall.wanoptapptype (keyword, WAN optimization app type)
      - fortinet.firewall.wanout (long, WAN out)
      - fortinet.firewall.weakwepiv (keyword, weak WEP IV)
      - fortinet.firewall.xauthgroup (keyword, XAuth group)
      - fortinet.firewall.xauthuser (keyword, XAuth user)
      - fortinet.firewall.xid (keyword, XID)
       
      
      === 4. LOG / EVENT / RULE METADATA ===
      - @timestamp (date, primary timestamp field)
      - _data_stream_timestamp (date, data stream timestamp)
      - _doc_count (long, document count)
      - _feature (keyword, feature information)
      - _field_names (keyword, field names)
      - _id (keyword, document ID)
      - _ignored (keyword, ignored fields)
      - _ignored_source (keyword, ignored source)
      - _index (keyword, index name)
      - _index_mode (keyword, index mode)
      - _inference_fields (keyword, inference fields)
      - _nested_path (keyword, nested path)
      - _routing (keyword, routing information)
      - _seq_no (long, sequence number)
      - _source (object, source document)
      - _tier (keyword, data tier)
      - _version (long, document version)
      - agent (object, elastic agent information)
      - agent.ephemeral_id (keyword, agent ephemeral ID)
      - agent.id (keyword, agent identifier)
      - agent.name (keyword, agent name)
      - agent.name.text (text, agent name as text field)
      - agent.type (keyword, agent type)
      - agent.version (keyword, agent version)
      - ecs (object, ECS information)
      - ecs.version (keyword, ECS version)
      - elastic_agent (object, elastic agent details)
      - elastic_agent.id (keyword, elastic agent ID)
      - elastic_agent.snapshot (boolean, whether elastic agent is snapshot)
      - elastic_agent.version (keyword, elastic agent version)
      - event (object, event information)
      - event.action (keyword, e.g., "login", "logout", "accept", "deny", "close", "server-rst", "client-rst", "dns", "timeout", "ssl-anomaly", "logged-on", "signature", "logged-off", "ssh_login", "Health Check")
      - event.agent_id_status (keyword, agent ID status)
      - event.category (keyword, event category group)
      - event.code (keyword, event code)
      - event.dataset (keyword, event dataset: "fortinet_fortigate.log")
      - event.duration (long, event duration in milliseconds)
      - event.id (keyword, event identifier)
      - event.ingested (date, event ingestion timestamp)
      - event.kind (keyword, event kind)
      - event.message (text, event message)
      - event.module (keyword, event module: "fortinet")
      - event.outcome (keyword, event outcome: success/failure)
      - event.reason (keyword, event reason)
      - event.reference (keyword, event reference)
      - event.start (date, event start time)
      - event.timezone (keyword, event timezone)
      - event.type (keyword, event type: connection/denied/configuration)
      - log (object, log information)
      - log.file (object, log file information)
      - log.file.path (keyword, log file path)
      - log.flags (keyword, log flags)
      - log.level (keyword, e.g., "info", "error", "information", "warning", "notice", "alert", "warn", "critical")
      - log.offset (long, log file offset)
      - log.source (object, log source information)
      - log.source.address (keyword, log source address)
      - log.syslog (object, syslog information)
      - log.syslog.facility (object, syslog facility)
      - log.syslog.facility.code (long, syslog facility code)
      - log.syslog.priority (long, syslog priority)
      - log.syslog.severity (object, syslog severity)
      - log.syslog.severity.code (long, syslog severity code)
      - rule (object, rule information)
      - rule.category (keyword, rule category: firewall/p2p/web)
      - rule.description (text, rule description)
      - rule.id (keyword, rule identifier)
      - rule.name (keyword, e.g., "TO_INTERNET_SDWAN", "AD_SERVICES", "BLOCK_EXTERNAL_DNS", "AP_CONTROLLER", "SNMP_SERVICE", "TO_AP_CONTROLLER", "PRINT_CONTROLLER_CONNECT", "TO_VMS_CAMERA", "ADMIN_MGMT", "HTTP_HTTPs_SERVICES", "TO_JBSIGN")
      - rule.ruleset (keyword, rule set name)
      - rule.uuid (keyword, rule UUID)
      - message (text, general message field)



      === 5. MISC / OTHER ===
      - data_stream (object, data stream information)
      - data_stream.dataset (keyword, data stream dataset)
      - data_stream.namespace (keyword, data stream namespace)
      - data_stream.type (keyword, data stream type)
      - error (object, error information)
      - error.code (keyword, error code)
      - error.message (text, error message)
      - file (object, file information)
      - file.extension (keyword, file extension)
      - file.name (keyword, file name)
      - file.size (long, file size in bytes)
      - fortinet (object, fortinet general information)
      - fortinet.file (object, fortinet file information)
      - fortinet.file.hash (object, fortinet file hash information)
      - fortinet.file.hash.crc32 (keyword, fortinet file CRC32 hash)
      - fortinet.firewall (object, fortinet firewall information)
      - input (object, input information)
      - input.type (keyword, input type)
      - related (object, related information)
      - related.hash (keyword, array of related hashes)
      - related.hosts (keyword, array of related hostnames)
      - related.ip (ip, array of related IP addresses)
      - tags (keyword, array of tags)
      - threat (object, threat information)
      - threat.feed (object, threat feed information)
      - threat.feed.name (keyword, threat feed name)
      - vulnerability (object, vulnerability information)
      - vulnerability.category (keyword, vulnerability category)
      
      
      
      === 6. NETWORK & TRAFFIC ===
      - destination (object, destination information)
      - destination.address (keyword, destination address)
      - destination.as (object, destination autonomous system information)
      - destination.as.number (long, destination ASN number)
      - destination.as.organization (object, destination AS organization)
      - destination.as.organization.name (keyword, external organization name, e.g., "Google LLC", "Amazon.com", "Microsoft Corporation")
      - destination.as.organization.name.text (text, destination AS organization name as text field)
      - destination.bytes (long, bytes sent to destination)
      - destination.domain (keyword, destination domain/hostname)
      - destination.geo (object, destination geographic information)
      - destination.geo.city_name (keyword, city of destination IP)
      - destination.geo.continent_name (keyword, continent name of destination IP)
      - destination.geo.country_iso_code (keyword, country ISO code of destination IP)
      - destination.geo.country_name (keyword, country of destination IP)
      - destination.geo.location (geo_point, destination geographic coordinates)
      - destination.geo.name (keyword, destination location name)
      - destination.geo.region_iso_code (keyword, region ISO code of destination IP)
      - destination.geo.region_name (keyword, region name of destination IP)
      - destination.ip (ip, destination IP address)
      - destination.mac (keyword, MAC address of destination)
      - destination.nat (object, destination NAT information)
      - destination.nat.ip (ip, destination NAT IP address)
      - destination.nat.port (long, destination NAT port)
      - destination.packets (long, packets sent to destination)
      - destination.port (long, destination port number)
      - destination.user (object, destination user information)
      - destination.user.email (keyword, destination user email)
      - destination.user.group (object, destination user group)
      - destination.user.group.name (keyword, destination user group name)
      - destination.user.name (keyword, destination user name)
      - destination.user.name.text (text, destination user name as text field)
      - network (object, network information)
      - network.application (keyword, network application protocol)
      - network.bytes (long, total bytes for traffic analysis)
      - network.direction (keyword, traffic direction: inbound/outbound/internal)
      - network.iana_number (long, IANA protocol number)
      - network.packets (long, total packet count)
      - network.protocol (keyword, network protocol: TCP/UDP/ICMP)
      - network.transport (keyword, transport protocol: tcp/udp)
      - source (object, source information)
      - source.address (keyword, source address)
      - source.as (object, source autonomous system information)
      - source.as.number (long, source ASN number)
      - source.as.organization (object, source AS organization)
      - source.as.organization.name (keyword, source organization name)
      - source.as.organization.name.text (text, source AS organization name as text field)
      - source.bytes (long, bytes sent from source)
      - source.geo (object, source geographic information)
      - source.geo.city_name (keyword, city of source IP)
      - source.geo.continent_name (keyword, continent name of source IP)
      - source.geo.country_iso_code (keyword, country ISO code of source IP)
      - source.geo.country_name (keyword, country of source IP)
      - source.geo.location (geo_point, source geographic coordinates)
      - source.geo.name (keyword, source location name)
      - source.geo.region_iso_code (keyword, region ISO code of source IP)
      - source.geo.region_name (keyword, region name of source IP)
      - source.ip (ip, source IP address)
      - source.mac (keyword, MAC address of source)
      - source.nat (object, source NAT information)
      - source.nat.ip (ip, source NAT IP address)
      - source.nat.port (long, source NAT port)
      - source.packets (long, packets sent from source)
      - source.port (long, source port number)
      - source.user (object, source user information)
      - source.user.domain (keyword, source user domain)
      - source.user.email (keyword, source user email address)
      - source.user.group (object, source user group)
      - source.user.group.name (keyword, source user group name)
      - source.user.id (keyword, source user ID)
      - source.user.name (keyword, source user name)
      - source.user.name.text (text, source user name as text field)
      - source.user.roles (keyword, source user roles, e.g., "Administrator")
      
      === 7. THREAT / SECURITY ===
      - fortinet.firewall.alert (keyword, firewall alert information)
      - fortinet.firewall.attack (keyword, attack signature/name)
      - fortinet.firewall.attackcontext (keyword, attack context information)
      - fortinet.firewall.attackcontextid (keyword, attack context identifier)
      - fortinet.firewall.attackid (keyword, attack signature ID)
      - fortinet.firewall.suspicious (keyword, suspicious activity indicator)
      - fortinet.firewall.threattype (keyword, type of threat detected)
      - fortinet.firewall.virus (keyword, virus/malware name)
      - fortinet.firewall.virusid (keyword, virus/malware identifier)
      - fortinet.firewall.vulncat (keyword, vulnerability category)
      - fortinet.firewall.vulnid (keyword, vulnerability identifier)
      - fortinet.firewall.vulnname (keyword, vulnerability name)
      
      === 8. USER / IDENTITY / EMAIL ===
      - email (object, email information)
      - email.cc (object, email CC recipients)
      - email.cc.address (keyword, email CC recipient addresses)
      - email.from (object, email sender information)
      - email.from.address (keyword, email sender address)
      - email.sender (object, email sender details)
      - email.sender.address (keyword, email sender address)
      - email.subject (keyword, email subject line)
      - email.subject.text (text, email subject line as text field)
      - email.to (object, email recipients)
      - email.to.address (keyword, email recipient addresses)
      - related.user (keyword, array of related usernames)
      - user (object, general user information)
      - user.domain (keyword, user domain)
      - user.email (keyword, general user email)
      - user.id (keyword, general user ID)
      - user.name (keyword, general user field, not source/destination specific)
      - user.name.text (text, user name as text field)
      - user.roles (keyword, user roles)
      - user_agent (object, user agent information)
      - user_agent.original (text, original user agent string)
      - user_agent.original.text (text, original user agent string as text field)
      
      
      IMPORTANT ROLE MAPPINGS:
      - Questions about "admin", "ad", "administrator" should use "Administrator" (capitalized)
      - Always normalize roles: admin/ad/administrator → Administrator
      - Example query: {"term": {"source.user.roles": "Administrator"}}

      IMPORTANT FIELD MAPPINGS (Vietnamese → English):
      - "tổ chức", "organization", "công ty" → use "destination.as.organization.name"
      - "người dùng", "user" → use "source.user.name"
      - "địa chỉ IP", "IP address" → use "source.ip" or "destination.ip"
      - "hành động", "action" → use "event.action"
      - "bytes", "dung lượng", "traffic" → use "network.bytes"
      - "packets", "gói tin" → use "network.packets"
      - "mức rủi ro", "risk level", "crlevel" → use "fortinet.firewall.crlevel"
      - "tấn công", "attack", "signature" → use "fortinet.firewall.attack"

      GEOGRAPHIC & DIRECTION MAPPINGS (CRITICAL):
      - "Việt Nam", "Vietnam", "VN" → use "Vietnam" (exact match)
      - "nước ngoài", "foreign", "international" → use must_not with source/destination country
      - "từ Việt Nam ra nước ngoài" → source.geo.country_name: "Vietnam" AND must_not destination.geo.country_name: "Vietnam"
      - "outbound", "ra ngoài", "đi ra" → use "network.direction": "outbound"
      - "inbound", "vào trong", "đi vào" → use "network.direction": "inbound"
      - "internal", "nội bộ", "trong mạng" → use "network.direction": "internal"
      - "source country" → use "source.geo.country_name"
      - "destination country" → use "destination.geo.country_name"

      FIREWALL ACTION MAPPINGS (CRITICAL):
      - "chặn", "block", "deny", "từ chối" → use "fortinet.firewall.action": "deny"
      - "cho phép", "allow", "accept", "thông qua" → use "fortinet.firewall.action": "allow"
      - "rule chặn nhiều nhất" → filter by action: "deny" + terms agg on "rule.name"
      - "rule cho phép nhiều nhất" → filter by action: "allow" + terms agg on "rule.name"
      - "rule name" → use "rule.name" (NOT fortinet.firewall.ruleid)
      - "policy ID" → use "fortinet.firewall.policyid"
      - "rule ID" → use "fortinet.firewall.ruleid"

      NAT MAPPINGS (CRITICAL):
      - "DNAT", "destination NAT" → use "fortinet.firewall.trandisp": "dnat"
      - "SNAT", "source NAT" → use "fortinet.firewall.trandisp": "snat"
      - "NAT translation IP" → use "fortinet.firewall.transip"
      - "NAT translation ID" → use "fortinet.firewall.translationid"
      - "destination NAT IP" → use "destination.nat.ip"
      - "source NAT IP" → use "source.nat.ip"
      - For DNAT queries: use "fortinet.firewall.transip" or "destination.nat.ip" for internal server IP

      QUERY STRUCTURE BEST PRACTICES:
      - Use "filter" instead of "must" for exact matches and ranges for better performance
      - For time ranges, prefer "gte": "now-24h" over absolute timestamps when possible
      - For aggregations with sorting, use proper order syntax: "order": {"agg_name": "desc"}
      - Example structure: {"bool": {"filter": [{"term": {...}}, {"range": {...}}]}}
      - For terms aggregation, check if field supports aggregation
      - If unsure about field type, use simple field name without .keyword
      - Example: use "source.user.name" not "source.user.name.keyword"
      - Non-aggregation queries MUST set "size": 50; aggregation queries MUST set "size": 0
      
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
    return List.of(getCategoryGuides());
  }

  /**
   * Hướng dẫn ngắn gọn theo 8 category (mục tiêu: ngắn, dễ dùng trong prompt)
   */
  public static String getCategoryGuides() {
    return """
      SCHEMA CATEGORIES GUIDE (use exact field names):

      1) APPLICATION/URL/DNS/HTTP/TLS
         - Purpose: app-level questions (domains, URLs, TLS details)
         - Key: url.*, dns.question.*, http.request.*, tls.*
         - Examples: top domains, TLS versions, HTTP methods

      2) DEVICE/HOST/CONTAINER/CLOUD
         - Purpose: asset identity and platform
         - Key: host.*, container.*, cloud.*, observer.*
         - Examples: by host.name, cloud.instance.id, observer.name

      3) FORTINET/FIREWALL METADATA
         - Purpose: FortiGate-specific context (policy, action, IPS)
         - Key: fortinet.firewall.* (action, policyid, ruleid, attack, crlevel)
         - Examples: filter by action deny/allow, policyid, top attacks

      4) LOG/EVENT/RULE METADATA
         - Purpose: generic event and rule info
         - Key: @timestamp, event.*, rule.*, log.*
         - Examples: time filters, rule.name group-by, event.action

      5) MISC/OTHER
         - Purpose: supplemental fields
         - Key: file.*, related.*, tags, threat.*, vulnerability.*
         - Examples: correlate by related.ip, file.name

      6) NETWORK & TRAFFIC
         - Purpose: conversations, bytes/packets, direction
         - Key: source.*, destination.*, network.* (bytes, packets, direction)
         - Examples: top destination.ip by bytes; outbound/inbound/internal

      7) THREAT/SECURITY (IPS)
         - Purpose: detections, signatures, risk
         - Key: fortinet.firewall.attack, attackid, crlevel
         - Examples: top signatures, risk distribution, high/critical

      8) USER/IDENTITY/EMAIL
         - Purpose: user-centric queries
         - Key: user.*, source.user.*, destination.user.*, email.*
         - Examples: logs by user.name, role = Administrator

      NOTE:
      - Normalize roles: admin/ad/administrator → Administrator
      - Prefer bool.filter for exact matches and ranges
      - Time ranges: use now-<X> (e.g., now-24h, now-7d) with @timestamp
      - For counting: use aggs.value_count on @timestamp and size: 0
      - For ranking: use terms agg and optional sum(network.bytes/packets)
      """;
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
        Response: {"body":"{\\"query\\":{\\"bool\\":{\\"must\\":[{\\"term\\":{\\"source.user.roles\\":\\"Administrator\\"}},{\\"range\\":{\\"@timestamp\\":{\\"gte\\":\\"2025-09-11T00:00:00.000+07:00\\",\\"lte\\":\\"2025-09-11T23:59:59.999+07:00\\"}}}]}} ,\\"size\\":50}","query":1}
        """;
  }

  /**
   * Trả về examples về network traffic analysis
   */
  public static String getNetworkTrafficExamples() {
    return """
        📊 NETWORK TRAFFIC ANALYSIS PATTERNS:
        
        🔥 Pattern 1: "Top IP destinations by traffic"
        Q: "IP đích nào nhận nhiều traffic nhất hôm nay?"
        ✅ {"query":{"range":{"@timestamp":{"gte":"now-24h"}}},"aggs":{"top_destinations":{"terms":{"field":"destination.ip","size":10,"order":{"total_bytes":"desc"}},"aggs":{"total_bytes":{"sum":{"field":"network.bytes"}}}}},"size":0}
        
        🌍 Pattern 2: "Vietnam outbound traffic" (CRITICAL)
        Q: "Kết nối từ Việt Nam ra nước ngoài trong 6 giờ qua"
        ✅ {"query":{"bool":{"must":[{"term":{"network.direction":"outbound"}},{"term":{"source.geo.country_name":"Vietnam"}}],"must_not":[{"term":{"destination.geo.country_name":"Vietnam"}}],"filter":[{"range":{"@timestamp":{"gte":"now-6h"}}}]}},"size":50}
        
        📈 Pattern 3: "Top organizations by bytes"
        Q: "Tổ chức nào được truy cập nhiều nhất theo bytes?"
        ✅ {"query":{"range":{"@timestamp":{"gte":"now-24h"}}},"aggs":{"top_orgs":{"terms":{"field":"destination.as.organization.name","size":10,"order":{"total_bytes":"desc"}},"aggs":{"total_bytes":{"sum":{"field":"network.bytes"}}}}},"size":0}
        
        💡 KEY RULES:
        - Vietnam outbound = source:Vietnam + must_not destination:Vietnam + direction:outbound
        - Use "sum" + "order":"desc" for "nhiều nhất" by bytes/packets
        - "filter" array for time ranges + exact matches
        """;
  }

  /**
   * Trả về examples về IPS security analysis
   */
  public static String getIPSSecurityExamples() {
    return """
        IPS SECURITY ANALYSIS EXAMPLES:
        
        1. High/Critical IPS events in last 24 hours:
        Question: "Liệt kê các phiên có mức rủi ro IPS cao (crlevel = high/critical) trong 1 ngày qua"
        Correct Query Structure:
        {
          "query": {
            "bool": {
              "filter": [
                { "range": { "@timestamp": { "gte": "now-24h" } } },
                { "terms": { "fortinet.firewall.crlevel": ["high", "critical"] } }
              ]
            }
          },
          "sort": [{ "@timestamp": "desc" }]
        }
        
        2. Top attack signatures by count:
        {
          "query": {
            "bool": {
              "filter": [
                { "range": { "@timestamp": { "gte": "now-7d" } } },
                { "exists": { "field": "fortinet.firewall.attack" } }
              ]
            }
          },
          "aggs": {
            "top_attacks": {
              "terms": { "field": "fortinet.firewall.attack", "size": 10 },
              "aggs": {
                "risk_levels": { "terms": { "field": "fortinet.firewall.crlevel" } }
              }
            }
          },
          "size": 0
        }
        
        3. IPS events by risk level distribution:
        {
          "query": {
            "bool": {
              "filter": [
                { "range": { "@timestamp": { "gte": "now-24h" } } },
                { "exists": { "field": "fortinet.firewall.crlevel" } }
              ]
            }
          },
          "aggs": {
            "risk_distribution": {
              "terms": { "field": "fortinet.firewall.crlevel", "size": 10 }
            }
          },
          "size": 0
        }
        
        IPS FIELD MAPPINGS:
        - "mức rủi ro", "risk level", "crlevel" → use "fortinet.firewall.crlevel"
        - "attack", "tấn công", "signature" → use "fortinet.firewall.attack"
        - "attack ID", "signature ID" → use "fortinet.firewall.attackid"
        - For multiple risk levels, use "terms" filter: {"terms": {"fortinet.firewall.crlevel": ["high", "critical"]}}
        """;
  }

  /**
   * Trả về examples về geographic và direction analysis
   */
  public static String getGeographicExamples() {
    return """
        GEOGRAPHIC & DIRECTION ANALYSIS EXAMPLES:
        
        1. Outbound traffic from Vietnam to foreign countries:
        Question: "Trong 7 ngày qua, các kết nối outbound từ Việt Nam ra nước ngoài (không phải Việt Nam) là gì?"
        CORRECT Query Structure:
        {
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
          },
          "size": 50
        }
        
        2. Inbound traffic to Vietnam from foreign countries:
        Question: "Traffic vào Việt Nam từ nước ngoài trong 24 giờ qua"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "must": [
                { "term": { "network.direction": "inbound" } },
                { "term": { "destination.geo.country_name": "Vietnam" } }
              ],
              "must_not": [
                { "term": { "source.geo.country_name": "Vietnam" } }
              ],
              "filter": [
                { "range": { "@timestamp": { "gte": "now-24h" } } }
              ]
            }
          },
          "size": 50
        }
        
        3. Internal Vietnam traffic:
        Question: "Traffic nội bộ trong Việt Nam hôm nay"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "must": [
                { "term": { "network.direction": "internal" } },
                { "term": { "source.geo.country_name": "Vietnam" } },
                { "term": { "destination.geo.country_name": "Vietnam" } }
              ],
              "filter": [
                { "range": { "@timestamp": { "gte": "now-24h" } } }
              ]
            }
          },
          "size": 50
        }
        
        CRITICAL GEOGRAPHIC RULES:
        - ALWAYS use "Vietnam" (not "Việt Nam") in Elasticsearch queries
        - "từ X ra nước ngoài" = source: X + must_not destination: X
        - "vào X từ nước ngoài" = destination: X + must_not source: X  
        - "nội bộ X" = source: X + destination: X
        - Use must_not for exclusion, NOT conflicting positive filters
        - Combine with network.direction when mentioned (outbound/inbound/internal)
        """;
  }

  /**
   * Trả về examples về firewall rule analysis
   */
  public static String getFirewallRuleExamples() {
    return """
        FIREWALL RULE ANALYSIS EXAMPLES:
        
        1. Top blocking rules in last 24 hours:
        Question: "Những rule nào chặn nhiều nhất trong 24 giờ qua?"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "filter": [
                { "term": { "fortinet.firewall.action": "deny" } },
                { "range": { "@timestamp": { "gte": "now-24h" } } }
              ]
            }
          },
          "aggs": {
            "top_rules": {
              "terms": { "field": "rule.name", "size": 10 }
            }
          },
          "size": 0
        }
        
        2. Top allowing rules by traffic volume:
        Question: "Rule nào cho phép traffic nhiều nhất hôm nay?"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "filter": [
                { "term": { "fortinet.firewall.action": "allow" } },
                { "range": { "@timestamp": { "gte": "now-24h" } } }
              ]
            }
          },
          "aggs": {
            "rules_by_traffic": {
              "terms": { "field": "rule.name", "size": 10, "order": { "total_bytes": "desc" } },
              "aggs": {
                "total_bytes": { "sum": { "field": "network.bytes" } }
              }
            }
          },
          "size": 0
        }
        
        3. Rules with most connections:
        Question: "Rule nào có nhiều connection nhất trong tuần qua?"
        CORRECT Query Structure:
        {
          "query": {
            "bool": {
              "filter": [
                { "range": { "@timestamp": { "gte": "now-7d" } } }
              ]
            }
          },
          "aggs": {
            "rules_by_connections": {
              "terms": { "field": "rule.name", "size": 10 }
            }
          },
          "size": 0
        }
        
        CRITICAL FIREWALL RULE RULES:
        - "chặn nhiều nhất" = filter by action: "deny" + terms agg (default sort by doc_count desc)
        - "cho phép nhiều nhất" = filter by action: "allow" + terms agg
        - Use "rule.name" for rule names, NOT "fortinet.firewall.ruleid"
        - For "nhiều nhất" questions, default terms aggregation sorts by count automatically
        - Don't create complex nested aggregations unless specifically needed
        - Use simple terms agg: {"terms": {"field": "rule.name", "size": 10}}
        """;
  }

  /**
   * Trả về examples về counting và statistical queries
   */
  public static String getCountingExamples() {
    return """
        🔢 COUNTING & STATISTICAL PATTERNS:
        
        CRITICAL: For counting questions ("đếm", "tổng số", "bao nhiêu"), ALWAYS include:
        - aggs with value_count on @timestamp
        - size: 0 (to return only aggregation results, not documents)
        
        🎯 Pattern 1: "Count logs from user"
        Q: "tổng số log hôm nay của QuynhTX"
        KEYWORDS DETECTED: "tổng số" = COUNTING QUERY
        ✅ {"query":{"bool":{"filter":[{"term":{"source.user.name":"QuynhTX"}},{"range":{"@timestamp":{"gte":"now/d"}}}]}},"aggs":{"total_count":{"value_count":{"field":"@timestamp"}}},"size":0}
        
        📅 Pattern 2: "Daily statistics"
        Q: "Thống kê số log theo ngày trong tuần qua"
        ✅ {"query":{"range":{"@timestamp":{"gte":"now-7d"}}},"aggs":{"daily_stats":{"date_histogram":{"field":"@timestamp","calendar_interval":"day"}}},"size":0}
        
        ⏰ Pattern 3: "Hourly breakdown today"
        Q: "Phân tích theo giờ trong ngày hôm nay"
        ✅ {"query":{"range":{"@timestamp":{"gte":"now-24h"}}},"aggs":{"hourly_breakdown":{"date_histogram":{"field":"@timestamp","calendar_interval":"hour"}}},"size":0}
        
        🏆 Pattern 4: "Top users by activity"
        Q: "User nào hoạt động nhiều nhất hôm nay?"
        ✅ {"query":{"range":{"@timestamp":{"gte":"now-24h"}}},"aggs":{"top_users":{"terms":{"field":"source.user.name","size":10}}},"size":0}
        
        🚨 CRITICAL COUNTING KEYWORDS:
        "tổng", "count", "bao nhiêu", "số lượng", "đếm" → ALWAYS use value_count + size:0
        "theo ngày", "daily" → date_histogram + calendar_interval:day
        "theo giờ", "hourly" → date_histogram + calendar_interval:hour
        "nhiều nhất", "top" → terms agg (auto sorts by doc_count desc)
        """;
  }

  /**
   * Quick reference cho các pattern phổ biến
   */
  public static String getQuickPatterns() {
    return """
        🚀 QUICK PATTERNS REFERENCE:
        
        📋 BASIC SEARCHES:
        • User logs: {"query":{"bool":{"filter":[{"term":{"source.user.name":"USERNAME"}},{"range":{"@timestamp":{"gte":"now-24h"}}}]}},"size":50}
        • Admin activity: {"query":{"bool":{"filter":[{"term":{"source.user.roles":"Administrator"}},{"range":{"@timestamp":{"gte":"now-24h"}}}]}},"size":50}
        • Blocked traffic: {"query":{"bool":{"filter":[{"term":{"fortinet.firewall.action":"deny"}},{"range":{"@timestamp":{"gte":"now-24h"}}}]}},"size":50}
        
        📊 COUNTING QUERIES:
        • Count total: {"query":{"range":{"@timestamp":{"gte":"now-24h"}}},"aggs":{"total":{"value_count":{"field":"@timestamp"}}},"size":0}
        • Count by user: {"query":{"range":{"@timestamp":{"gte":"now-24h"}}},"aggs":{"by_user":{"terms":{"field":"source.user.name"}}},"size":0}
        
        🔍 ANALYSIS QUERIES:
        • Users for IP: {"query":{"bool":{"filter":[{"term":{"source.ip":"10.6.99.78"}}]}},"aggs":{"users":{"terms":{"field":"source.user.name","size":10}}},"size":0}
        • IPs for user: {"query":{"bool":{"filter":[{"term":{"source.user.name":"USERNAME"}}]}},"aggs":{"ips":{"terms":{"field":"source.ip","size":10}}},"size":0}
        
        🔝 TOP RANKINGS:
        • Top destinations: {"query":{"range":{"@timestamp":{"gte":"now-24h"}}},"aggs":{"top_dst":{"terms":{"field":"destination.ip","size":10}}},"size":0}
        • Top rules: {"query":{"range":{"@timestamp":{"gte":"now-24h"}}},"aggs":{"top_rules":{"terms":{"field":"rule.name","size":10}}},"size":0}
        
        🌍 GEOGRAPHIC:
        • Vietnam outbound: [Moved to QueryTemplates.OUTBOUND_CONNECTIONS_FROM_VIETNAM]
        
        🔄 NAT QUERIES:
        • DNAT to server: [See QueryTemplates.getDnatSessionsToInternalServer()]
        • SNAT from IP: {"query":{"bool":{"filter":[{"term":{"fortinet.firewall.trandisp":"snat"}},{"term":{"source.ip":"192.168.1.100"}},{"range":{"@timestamp":{"gte":"now-24h"}}}]}},"size":50}
        
        🔐 PROTOCOL QUERIES:
        • RDP from WAN: [Moved to QueryTemplates.RDP_TRAFFIC_FROM_WAN]
        • SSH to server: {"query":{"bool":{"filter":[{"term":{"destination.port":22}},{"term":{"destination.ip":"10.0.0.10"}},{"range":{"@timestamp":{"gte":"now-24h"}}}]}},"size":50}
        
        🚨 SECURITY THREAT DETECTION:
        • Brute force login: [Moved to QueryTemplates.BRUTE_FORCE_DETECTION]
        • Port scanning: [Moved to QueryTemplates.PORT_SCAN_DETECTION]
        • Data exfiltration: [Moved to QueryTemplates.DATA_EXFILTRATION_DETECTION]
        • Excessive admin port connections 15m: [Moved to QueryTemplates.EXCESSIVE_ADMIN_PORT_CONNECTIONS]
        • Blocked ICMP by user 1h: [Moved to QueryTemplates.BLOCKED_ICMP_BY_USER]
        
        ⚙️ CONFIGURATION MONITORING:
        • Policy changes: [Moved to QueryTemplates.FIREWALL_RULE_CHANGES]
        • Interface changes: [Moved to QueryTemplates.WAN_INTERFACE_CHANGES]
        • Shaping policy: [Moved to QueryTemplates.SHAPING_POLICY_CHANGES]
        • CNHN_ZONE changes: {"query":{"bool":{"filter":[{"term":{"source.user.name":"tanln"}},{"match":{"message":"CNHN_ZONE"}},{"exists":{"field":"fortinet.firewall.cfgattr"}}]}},"_source":["@timestamp","source.user.name","source.ip","message","fortinet.firewall.cfgattr"],"sort":[{"@timestamp":"asc"}],"size":200}

        
        🚫 BLOCKED ACTIVITIES:
        • SSH blocked: [Moved to QueryTemplates.BLOCKED_SSH_CONNECTIONS_BY_USER]
        • RDP blocked: [Moved to QueryTemplates.BLOCKED_RDP_FROM_LAN]
        • P2P blocked: [Moved to QueryTemplates.BLOCKED_P2P_TRAFFIC]
        • Shaping policy drop on device: [See QueryTemplates.getDroppedTrafficByShapingPolicy()]


        👤 USER-LEVEL SECURITY QUERIES:
        • User login failures 48h: [See QueryTemplates.getFailedLoginsByUser()]
        • Admin login from foreign 48h: [Moved to QueryTemplates.ADMIN_LOGINS_FROM_FOREIGN_COUNTRIES]

        """;
  }

  /**
   * Returns a single sample log (as JSON string) to help AI infer queries
   */
  public static String examplelog() {
    return """
    {
  "took": 214,
  "timed_out": false,
  "_shards": {
    "total": 43,
    "successful": 41,
    "skipped": 0,
    "failed": 2,
    "failures": [
      {
        "shard": 0,
        "index": "my-index",
        "node": "vRDOncgnSEWjVd9BhFUpiA",
        "reason": {
          "type": "query_shard_exception",
          "reason": "No mapping found for [@timestamp] in order to sort on",
          "index_uuid": "bMXi4sUESaqmMy5S4NAdxA",
          "index": "my-index"
        }
      },
      {
        "shard": 0,
        "index": "test-index",
        "node": "vRDOncgnSEWjVd9BhFUpiA",
        "reason": {
          "type": "query_shard_exception",
          "reason": "No mapping found for [@timestamp] in order to sort on",
          "index_uuid": "n599wBABRvqCBSyVr-6YFQ",
          "index": "test-index"
        }
      }
    ]
  },
  "hits": {
    "total": {
      "value": 10000,
      "relation": "gte"
    },
    "max_score": null,
    "hits": [
      {
        "_index": ".ds-logs-fortinet_fortigate.log-default-2025.09.02-000001",
        "_id": "hovbepkBWAVxoLH7laLp",
        "_score": null,
        "_source": {
          "agent": {
            "name": "hpt-logsv-srv",
            "id": "f4af12e8-c55b-4073-8acd-2fada355eb70",
            "type": "filebeat",
            "ephemeral_id": "4c58ec4b-54b7-4030-94a6-65de782ff122",
            "version": "8.19.3"
          },
          "log": {
            "level": "notice",
            "source": {
              "address": "10.254.254.251:14955"
            },
            "syslog": {
              "severity": {
                "code": 5
              },
              "priority": 189,
              "facility": {
                "code": 23
              }
            }
          },
          "elastic_agent": {
            "id": "f4af12e8-c55b-4073-8acd-2fada355eb70",
            "version": "8.19.3",
            "snapshot": false
          },
          "destination": {
            "port": 53,
            "bytes": 185,
            "ip": "10.0.0.1",
            "packets": 1
          },
          "rule": {
            "ruleset": "policy",
            "name": "AD_SERVICES",
            "id": "5",
            "category": "unscanned",
            "uuid": "b724d8c0-774e-51f0-9f4f-71e4671938ad"
          },
          "source": {
            "port": 62578,
            "bytes": 68,
            "ip": "10.4.100.112",
            "mac": "50-F2-65-E6-61-08",
            "packets": 1
          },
          "tags": [
            "fortinet-fortigate",
            "fortinet-firewall",
            "forwarded"
          ],
          "network": {
            "protocol": "windows ad",
            "bytes": 253,
            "transport": "udp",
            "iana_number": "17",
            "packets": 2,
            "direction": "internal"
          },
          "input": {
            "type": "udp"
          },
          "observer": {
            "ingress": {
              "interface": {
                "name": "WiFi-HPTVIETNAM"
              }
            },
            "product": "Fortigate",
            "vendor": "Fortinet",
            "name": "FTG-CNHN",
            "serial_number": "FG121GTK25006437",
            "type": "firewall",
            "egress": {
              "interface": {
                "name": "port3"
              }
            }
          },
          "@timestamp": "2025-09-24T15:33:54.000+07:00",
          "ecs": {
            "version": "8.17.0"
          },
          "related": {
            "hosts": [
              "4516a5bd-0d2d-41f3-8f3c-fa33015b6e90"
            ],
            "ip": [
              "10.4.100.112",
              "10.0.0.1"
            ]
          },
          "data_stream": {
            "namespace": "default",
            "type": "logs",
            "dataset": "fortinet_fortigate.log"
          },
          "fortinet": {
            "firewall": {
              "srcintfrole": "lan",
              "sessionid": "88029730",
              "type": "traffic",
              "srccountry": "Reserved",
              "subtype": "forward",
              "mastersrcmac": "50:f2:65:e6:61:08",
              "action": "accept",
              "trandisp": "noop",
              "osname": "macOS",
              "srcserver": "0",
              "srcfamily": "Mac",
              "vd": "root",
              "dstintfrole": "undefined",
              "devtype": "Laptop",
              "srcname": "4516a5bd-0d2d-41f3-8f3c-fa33015b6e90",
              "srcswversion": "10.15.7",
              "dstcountry": "Reserved",
              "srchwversion": "MacBook Pro",
              "srchwvendor": "Apple"
            }
          },
          "host": {
            "name": "4516a5bd-0d2d-41f3-8f3c-fa33015b6e90"
          },
          "event": {
            "duration": 180000000000,
            "agent_id_status": "verified",
            "ingested": "2025-09-24T08:33:55Z",
            "code": "0000000013",
            "timezone": "+0700",
            "kind": "event",
            "start": "2025-09-24T15:33:53.219+07:00",
            "action": "accept",
            "category": [
              "network"
            ],
            "type": [
              "connection",
              "end",
              "allowed"
            ],
            "dataset": "fortinet_fortigate.log",
            "outcome": "success"
          }
        },
        "fields": {
          "rule.id": [
            "5"
          ],
          "fortinet.firewall.srchwversion": [
            "MacBook Pro"
          ],
          "fortinet.firewall.srchwvendor": [
            "Apple"
          ],
          "elastic_agent.version": [
            "8.19.3"
          ],
          "event.category": [
            "network"
          ],
          "observer.egress.interface.name": [
            "port3"
          ],
          "observer.ingress.interface.name": [
            "WiFi-HPTVIETNAM"
          ],
          "observer.vendor": [
            "Fortinet"
          ],
          "agent.name.text": [
            "hpt-logsv-srv"
          ],
          "rule.ruleset": [
            "policy"
          ],
          "log.level": [
            "notice"
          ],
          "source.ip": [
            "10.4.100.112"
          ],
          "agent.name": [
            "hpt-logsv-srv"
          ],
          "host.name": [
            "4516a5bd-0d2d-41f3-8f3c-fa33015b6e90"
          ],
          "event.agent_id_status": [
            "verified"
          ],
          "event.kind": [
            "event"
          ],
          "event.outcome": [
            "success"
          ],
          "log.syslog.severity.code": [
            5
          ],
          "source.packets": [
            1
          ],
          "rule.name": [
            "AD_SERVICES"
          ],
          "network.packets": [
            2
          ],
          "input.type": [
            "udp"
          ],
          "data_stream.type": [
            "logs"
          ],
          "fortinet.firewall.type": [
            "traffic"
          ],
          "observer.serial_number": [
            "FG121GTK25006437"
          ],
          "tags": [
            "fortinet-fortigate",
            "fortinet-firewall",
            "forwarded"
          ],
          "event.code": [
            "0000000013"
          ],
          "agent.id": [
            "f4af12e8-c55b-4073-8acd-2fada355eb70"
          ],
          "source.port": [
            62578
          ],
          "ecs.version": [
            "8.17.0"
          ],
          "observer.type": [
            "firewall"
          ],
          "log.source.address": [
            "10.254.254.251:14955"
          ],
          "fortinet.firewall.srcswversion": [
            "10.15.7"
          ],
          "network.iana_number": [
            "17"
          ],
          "agent.version": [
            "8.19.3"
          ],
          "related.hosts": [
            "4516a5bd-0d2d-41f3-8f3c-fa33015b6e90"
          ],
          "destination.bytes": [
            185
          ],
          "event.start": [
            "2025-09-24T08:33:53.219Z"
          ],
          "fortinet.firewall.subtype": [
            "forward"
          ],
          "fortinet.firewall.trandisp": [
            "noop"
          ],
          "fortinet.firewall.vd": [
            "root"
          ],
          "fortinet.firewall.srcserver": [
            0
          ],
          "fortinet.firewall.srcname": [
            "4516a5bd-0d2d-41f3-8f3c-fa33015b6e90"
          ],
          "destination.port": [
            53
          ],
          "fortinet.firewall.devtype": [
            "Laptop"
          ],
          "observer.name": [
            "FTG-CNHN"
          ],
          "fortinet.firewall.srccountry": [
            "Reserved"
          ],
          "fortinet.firewall.dstintfrole": [
            "undefined"
          ],
          "destination.packets": [
            1
          ],
          "agent.type": [
            "filebeat"
          ],
          "fortinet.firewall.dstcountry": [
            "Reserved"
          ],
          "source.mac": [
            "50-F2-65-E6-61-08"
          ],
          "event.module": [
            "fortinet_fortigate"
          ],
          "network.protocol": [
            "windows ad"
          ],
          "related.ip": [
            "10.4.100.112",
            "10.0.0.1"
          ],
          "network.bytes": [
            253
          ],
          "observer.product": [
            "Fortigate"
          ],
          "elastic_agent.snapshot": [
            false
          ],
          "fortinet.firewall.srcintfrole": [
            "lan"
          ],
          "log.syslog.priority": [
            189
          ],
          "network.direction": [
            "internal"
          ],
          "event.timezone": [
            "+0700"
          ],
          "source.bytes": [
            68
          ],
          "fortinet.firewall.srcfamily": [
            "Mac"
          ],
          "elastic_agent.id": [
            "f4af12e8-c55b-4073-8acd-2fada355eb70"
          ],
          "data_stream.namespace": [
            "default"
          ],
          "fortinet.firewall.action": [
            "accept"
          ],
          "fortinet.firewall.mastersrcmac": [
            "50:f2:65:e6:61:08"
          ],
          "destination.ip": [
            "10.0.0.1"
          ],
          "network.transport": [
            "udp"
          ],
          "event.duration": [
            180000000000
          ],
          "fortinet.firewall.sessionid": [
            88029730
          ],
          "rule.uuid": [
            "b724d8c0-774e-51f0-9f4f-71e4671938ad"
          ],
          "event.action": [
            "accept"
          ],
          "event.ingested": [
            "2025-09-24T08:33:55Z"
          ],
          "@timestamp": [
            "2025-09-24T08:33:54.000Z"
          ],
          "data_stream.dataset": [
            "fortinet_fortigate.log"
          ],
          "event.type": [
            "connection",
            "end",
            "allowed"
          ],
          "agent.ephemeral_id": [
            "4c58ec4b-54b7-4030-94a6-65de782ff122"
          ],
          "fortinet.firewall.osname": [
            "macOS"
          ],
          "rule.category": [
            "unscanned"
          ],
          "event.dataset": [
            "fortinet_fortigate.log"
          ],
          "log.syslog.facility.code": [
            23
          ]
        },
        "sort": [
          1758702834000
        ]
      }
    ]
  }
}
    """;
  }
}