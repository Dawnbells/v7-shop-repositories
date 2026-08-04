package cn.v7soft.admin.task;

public interface NodeHealthProbe {
    HealthProbeResult probe(String ipv4);
}
