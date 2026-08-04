package cn.v7soft.admin.task;

public record HealthProbeResult(boolean healthy, String detail) {

    public static HealthProbeResult healthy(String detail) {
        return new HealthProbeResult(true, detail);
    }

    public static HealthProbeResult unhealthy(String detail) {
        return new HealthProbeResult(false, detail);
    }
}
