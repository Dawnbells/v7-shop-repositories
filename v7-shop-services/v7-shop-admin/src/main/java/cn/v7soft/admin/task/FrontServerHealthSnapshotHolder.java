package cn.v7soft.admin.task;

import org.springframework.stereotype.Component;

/**
 * 持有健康检查的最新快照，供接口层读取。
 *
 * <p>{@link HealthCheckTask} 逻辑密集且有多个用例覆盖，不宜为了「让外部读一眼状态」去动它的内部结构，
 * 因此由它在每轮末尾往这里投递一份不可变快照，接口层只读这里。
 *
 * <p>当前为单副本部署，进程内存即唯一真相，用 volatile 引用做整体替换就够；若将来改为多副本，
 * 只需把这个类换成 Redis 实现，任务与接口层都不用动。
 */
@Component
public class FrontServerHealthSnapshotHolder {

    private volatile FrontServerHealthSnapshot snapshot = FrontServerHealthSnapshot.disabled();

    public FrontServerHealthSnapshot get() {
        return snapshot;
    }

    public void publish(FrontServerHealthSnapshot snapshot) {
        this.snapshot = snapshot;
    }
}
