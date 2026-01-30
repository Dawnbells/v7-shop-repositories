package cn.v7soft.admin.controller.req;

import lombok.Data;

import java.util.List;
import java.util.Map;
    @Data
public class RiskWebhookRequest {
        private String callbackType;
        private String ip;
        private long jsServedAt;
        private long callbackSent;
        private String uuid;
        private String pdKey;
        private String pdVal;
        private ProxyData proxy;
        private VpnData vpn;
        private ClientData client;
        private TestsData tests;

        @Data
        public static class ProxyData {
            private boolean isProxy;
            private int numPositiveTests;
            private int numTests;
            private int score;
            private String informal;
        }

        @Data
        public static class VpnData {
            private boolean isVpn;
            private int numPositiveTests;
            private int numTests;
            private int score;
            private String informal;
        }

        @Data
        public static class ClientData {
            private boolean isClientThreat;
            private int numPositiveTests;
            private int numTests;
            private String informal;
        }

        @Data
        public static class TestsData {
            private HeaderTest headers;
            private DatacenterTest datacenter;
            private BlocklistTest blocklist;
            private TcpIpFpTest tcpipFp;
            private WebrtcTest webrtc;
            private TimezoneTest timezone;
            private NetTest net;
            private FlowPatternTest flowPattern;
            private LatencyTest latency;

            @Data
            public static class HeaderTest {
                private boolean isProxy;
                private Info info;

                @Data
                public static class Info {
                    private String message;
                }
            }

            @Data
            public static class DatacenterTest {
                private boolean isProxy;
                private boolean isVpn;
                private Info info;

                @Data
                public static class Info {
                    private String ip;
                    private String rir;
                    private boolean isBogon;
                    private boolean isDatacenter;
                    private boolean isTor;
                    private boolean isProxy;
                    private boolean isVpn;
                    private boolean isAbuser;
                    private Company company;
                    private Asn asn;
                    private Location location;
                    private double elapsedMs;

                    @Data
                    public static class Company {
                        private String name;
                        private String domain;
                        private String type;
                        private String network;
                        private String whois;
                    }

                    @Data
                    public static class Asn {
                        private int asn;
                        private String route;
                        private String descr;
                        private String country;
                        private boolean active;
                        private String org;
                        private String domain;
                        private String abuse;
                        private String type;
                        private String created;
                        private String updated;
                        private String rir;
                        private String whois;
                    }

                    @Data
                    public static class Location {
                        private String country;
                        private String countryCode;
                        private String state;
                        private String city;
                        private double latitude;
                        private double longitude;
                        private String zip;
                        private String timezone;
                        private String localTime;
                        private long localTimeUnix;
                        private boolean isDst;
                    }
                }
            }

            @Data
            public static class BlocklistTest {
                private boolean isProxy;
                private boolean isVpn;
            }

            @Data
            public static class TcpIpFpTest {
                private boolean isProxy;
                private Info info;

                @Data
                public static class Info {
                    private Map<String, Double> avgScoreOsClass;
                    private String tcpIpHighestOs;
                    private String userAgentOs;
                }
            }

            @Data
            public static class WebrtcTest {
                private Boolean isProxy;
                private Info info;

                @Data
                public static class Info {
                    private Map<String, Object> webrtcIps;
                    private List<Object> allIps;
                    private String ip;
                }
            }

            @Data
            public static class TimezoneTest {
                private boolean isProxy;
                private boolean isVpn;
                private Info info;

                @Data
                public static class Info {
                    private boolean isProxyByTimezone;
                    private TimeDelta isProxyByTimeDelta;
                    private ClientTimeData clientTimeData;
                    private IpTimeData ipTimeData;

                    @Data
                    public static class TimeDelta {
                        private boolean proxyByTimeDelta;
                        private int delta;
                        private int deltaThreshold;
                    }

                    @Data
                    public static class ClientTimeData {
                        private long timestamp;
                        private String timeStr;
                        private String timeZone;
                    }

                    @Data
                    public static class IpTimeData {
                        private String country;
                        private String countryCode;
                        private String state;
                        private String city;
                        private double latitude;
                        private double longitude;
                        private String zip;
                        private String timezone;
                        private String localTime;
                        private long localTimeUnix;
                        private boolean isDst;
                    }
                }
            }

            @Data
            public static class NetTest {
                private boolean isProxy;
                private Info info;

                @Data
                public static class Info {
                    private DnsResolving dnsResolving;
                    private CanLoadScriptFromUncommonPort canLoadScriptFromUncommonPort;

                    @Data
                    public static class DnsResolving {
                        private int res;
                        private int perf;
                    }

                    @Data
                    public static class CanLoadScriptFromUncommonPort {
                        private int res;
                        private int perf;
                    }
                }
            }

            @Data
            public static class FlowPatternTest {
                private boolean isProxy;
                private Info info;

                @Data
                public static class Info {
                    private boolean isProxy;
                    private boolean isProxy2;
                    private int numFlows;
                    private int numProxy;
                    private int numProxy2;
                }
            }

            @Data
            public static class LatencyTest {
                private boolean isProxy;
                private Info info;

                @Data
                public static class Info {
                    private TcpIpStats tcpIpStats;
                    private WsLatencyStats wsLatencyStats;
                    private Connection connection;

                    @Data
                    public static class TcpIpStats {
                        private List<Double> samples;
                        private double min;
                    }

                    @Data
                    public static class WsLatencyStats {
                        private List<Double> samples;
                        private double min;
                    }

                    @Data
                    public static class Connection {
                        private long wsConnectionEstablished;
                        private String wsIp;
                        private long wsFirstMessage;
                        private long wsWaitForLastMessageTimeout;
                    }
                }
            }
        }
    }
