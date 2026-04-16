package cn.v7soft.core.utils;

import cn.hutool.core.date.SystemClock;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;

import java.io.Serializable;
import java.util.Date;
import java.util.Formatter;

/**
 * Twitter的Snowflake 算法<br>
 * 分布式系统中，有一些需要使用全局唯一ID的场景，有些时候我们希望能使用一种简单一些的ID，并且希望ID能够按照时间有序生成。
 *
 * <p>
 * snowflake的结构如下(每部分用-分开):<br>
 *
 * <pre>
 * 符号位（1bit）-...- 时间戳相对值（30bit）- 数据中心标志（1bit）- 机器标志（1bit）- 递增序号（5bit）
 * 0 - 00000000 00000000 00000000 000000000 00 - 0 - 0 - 00000
 *
 * 短ID，起始时间为2024年6月
 * 01101000100000001111011111111100
 * 符号位（1bit）- 时间戳相对值（30bit）- 数据中心标志（1bit）- 机器标志（1bit）- 递增序号（5bit）
 * 0 - 0000000000 0000000000 0000000000 0000000000 - 00000 - 0000 - 000000
 * </pre>
 * <p>
 * 第一位为未使用(符号位表示正数)，接下来的29位为100毫秒秒级时间(29位的长度可以使用10年)<br>
 * 然后是1位datacenterId和1位workerId(10位的长度最多支持部署1024个节点）<br>
 * 最后12位是100毫秒内的计数（12位的计数顺序号支持每个节点每毫秒产生4096个ID序号）
 * <p>
 * 并且可以通过生成的id反推出生成时间,datacenterId和workerId
 * <p>
 * 参考：http://www.cnblogs.com/relucent/p/4955340.html<br>
 * 关于长度是18还是19的问题见：https://blog.csdn.net/unifirst/article/details/80408050
 *
 * @author Looly
 * @since 3.0.1
 */
public class ShortSnowflake implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 默认的起始时间，起始时间为2023年1月1日 00:00:00, 每单元为125ms，13位约可以使用15年，2038年1月1日
     */
    public static long DEFAULT_TWEPOCH = 13380019200L;
    /**
     * 默认回拨时间，2S
     */
    public static long DEFAULT_TIME_OFFSET = 2L;

    private static final long WORKER_ID_BITS = 1L;
    // 最大支持机器节点数0~1，一共2个
    @SuppressWarnings({"PointlessBitwiseExpression", "FieldCanBeLocal"})
    private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);
    private static final long DATA_CENTER_ID_BITS = 1L;
    // 最大支持数据中心节点数0~1，一共2个
    @SuppressWarnings({"PointlessBitwiseExpression", "FieldCanBeLocal"})
    private static final long MAX_DATA_CENTER_ID = -1L ^ (-1L << DATA_CENTER_ID_BITS);
    // 序列号8位（表示只允许workId的范围为：0-255）1111 11
    private static final long SEQUENCE_BITS = 6L;
    // 机器节点左移12位
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    // 数据中心节点左移17位 1453472317440
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    // 时间毫秒数左移22位
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;
    // 序列掩码，用于限定序列最大值不能超过4095
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);// 4095

    /**
     * 时间戳每毫秒的时间单位
     */
    private static final long TWEPOCH_TIME_UNIT = 125;
    /**
     * 初始化时间点
     */
    private final long twepoch;
    private final long workerId;
    private final long dataCenterId;
    private final boolean useSystemClock;
    /**
     * 允许的时钟回拨毫秒数
     */
    private final long timeOffset;
    /**
     * 当在低频模式下时，序号始终为0，导致生成ID始终为偶数<br>
     * 此属性用于限定一个随机上限，在不同毫秒下生成序号时，给定一个随机数，避免偶数问题。<br>
     * 注意次数必须小于{@link #SEQUENCE_MASK}，{@code 0}表示不使用随机数。<br>
     * 这个上限不包括值本身。
     */
    private final long randomSequenceLimit;

    /**
     * 自增序号，当高频模式下时，同一毫秒内生成N个ID，则这个序号在同一毫秒下，自增以避免ID重复。
     */
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * 构造，使用自动生成的工作节点ID和数据中心ID
     */
    public ShortSnowflake() {
        this(IdUtil.getWorkerId(IdUtil.getDataCenterId(MAX_DATA_CENTER_ID), MAX_WORKER_ID));
    }

    /**
     * 构造
     *
     * @param workerId 终端ID
     */
    public ShortSnowflake(long workerId) {
        this(workerId, IdUtil.getDataCenterId(MAX_DATA_CENTER_ID));
    }

    /**
     * 构造
     *
     * @param workerId     终端ID
     * @param dataCenterId 数据中心ID
     */
    public ShortSnowflake(long workerId, long dataCenterId) {
        this(workerId, dataCenterId, false);
    }

    /**
     * 构造
     *
     * @param workerId         终端ID
     * @param dataCenterId     数据中心ID
     * @param isUseSystemClock 是否使用{@link SystemClock} 获取当前时间戳
     */
    public ShortSnowflake(long workerId, long dataCenterId, boolean isUseSystemClock) {
        this(null, workerId, dataCenterId, isUseSystemClock);
    }

    /**
     * @param epochDate        初始化时间起点（null表示默认起始日期）,后期修改会导致id重复,如果要修改连workerId dataCenterId，慎用
     * @param workerId         工作机器节点id
     * @param dataCenterId     数据中心id
     * @param isUseSystemClock 是否使用{@link SystemClock} 获取当前时间戳
     * @since 5.1.3
     */
    public ShortSnowflake(Date epochDate, long workerId, long dataCenterId, boolean isUseSystemClock) {
        this(epochDate, workerId, dataCenterId, isUseSystemClock, DEFAULT_TIME_OFFSET);
    }

    /**
     * @param epochDate        初始化时间起点（null表示默认起始日期）,后期修改会导致id重复,如果要修改连workerId dataCenterId，慎用
     * @param workerId         工作机器节点id
     * @param dataCenterId     数据中心id
     * @param isUseSystemClock 是否使用{@link SystemClock} 获取当前时间戳
     * @param timeOffset       允许时间回拨的毫秒数
     * @since 5.8.0
     */
    public ShortSnowflake(Date epochDate, long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset) {
        this(epochDate, workerId, dataCenterId, isUseSystemClock, timeOffset, 1);
    }

    /**
     * @param epochDate           初始化时间起点（null表示默认起始日期）,后期修改会导致id重复,如果要修改连workerId dataCenterId，慎用
     * @param workerId            工作机器节点id
     * @param dataCenterId        数据中心id
     * @param isUseSystemClock    是否使用{@link SystemClock} 获取当前时间戳
     * @param timeOffset          允许时间回拨的毫秒数
     * @param randomSequenceLimit 限定一个随机上限，在不同毫秒下生成序号时，给定一个随机数，避免偶数问题，0表示无随机，上限不包括值本身。
     * @since 5.8.0
     */
    public ShortSnowflake(Date epochDate, long workerId, long dataCenterId,
                          boolean isUseSystemClock, long timeOffset, long randomSequenceLimit) {
        this.twepoch = (null != epochDate) ? epochDate.getTime() : DEFAULT_TWEPOCH;
        this.workerId = Assert.checkBetween(workerId, 0, MAX_WORKER_ID);
        this.dataCenterId = Assert.checkBetween(dataCenterId, 0, MAX_DATA_CENTER_ID);
        this.useSystemClock = isUseSystemClock;
        this.timeOffset = timeOffset;
        this.randomSequenceLimit = Assert.checkBetween(randomSequenceLimit, 0, SEQUENCE_MASK);
    }

    /**
     * 根据Snowflake的ID，获取机器id
     *
     * @param id snowflake算法生成的id
     * @return 所属机器的id
     */
    public long getWorkerId(long id) {
        return id >> WORKER_ID_SHIFT & ~(-1L << WORKER_ID_BITS);
    }

    /**
     * 根据Snowflake的ID，获取数据中心id
     *
     * @param id snowflake算法生成的id
     * @return 所属数据中心
     */
    public long getDataCenterId(long id) {
        return id >> DATA_CENTER_ID_SHIFT & ~(-1L << DATA_CENTER_ID_BITS);
    }

    /**
     * 根据Snowflake的ID，获取生成时间
     *
     * @param id snowflake算法生成的id
     * @return 生成的时间
     */
    public long getGenerateDateTime(long id) {
        return (id >> TIMESTAMP_LEFT_SHIFT & ~(-1L << 41L)) + twepoch;
    }

    /**
     * 根据传入时间戳-计算ID起终点
     *
     * @param timestampStart 开始时间戳
     * @param timestampEnd   结束时间戳
     * @return key-ID起点，Value-ID终点
     * @since 5.8.23
     */
    public Pair<Long, Long> getIdScopeByTimestamp(long timestampStart, long timestampEnd) {
        return getIdScopeByTimestamp(timestampStart, timestampEnd, true);
    }

    /**
     * 根据传入时间戳-计算ID起终点 Gitee/issues/I60M14
     *
     * @param timestampStart        开始时间戳
     * @param timestampEnd          结束时间戳
     * @param ignoreCenterAndWorker 是否忽略数据中心和机器节点的占位，忽略后可获得分布式环境全局可信赖的起终点。
     * @return key-ID起点，Value-ID终点
     * @since 5.8.23
     */
    public Pair<Long, Long> getIdScopeByTimestamp(long timestampStart, long timestampEnd, boolean ignoreCenterAndWorker) {
        long startTimeMinId = ((timestampStart - twepoch) / TWEPOCH_TIME_UNIT) << TIMESTAMP_LEFT_SHIFT;
        long endTimeMinId = ((timestampEnd - twepoch) / TWEPOCH_TIME_UNIT) << TIMESTAMP_LEFT_SHIFT;
        if (ignoreCenterAndWorker) {
            long endId = endTimeMinId | ~(-1 << TIMESTAMP_LEFT_SHIFT);
            return Pair.of(startTimeMinId, endId);
        } else {
            long startId = startTimeMinId | (dataCenterId << DATA_CENTER_ID_SHIFT) | (workerId << WORKER_ID_SHIFT);
            long endId = endTimeMinId | (dataCenterId << DATA_CENTER_ID_SHIFT) | (workerId << WORKER_ID_SHIFT) | SEQUENCE_MASK;
            return Pair.of(startId, endId);
        }
    }

    /**
     * 下一个ID
     *
     * @return ID
     */
    public synchronized long nextId() {
        long timestamp = genTime();
        if (timestamp < this.lastTimestamp) {
            if (this.lastTimestamp - timestamp < timeOffset) {
                // 容忍指定的回拨，避免NTP校时造成的异常
                timestamp = lastTimestamp;
            } else {
                // 如果服务器时间有问题(时钟后退) 报错。
                throw new IllegalStateException(StrUtil.format("Clock moved backwards. Refusing to generate id for {}ms", lastTimestamp - timestamp));
            }
        }
        if (timestamp == this.lastTimestamp) {
            final long sequence = (this.sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
            this.sequence = sequence;
        } else {
            // issue#I51EJY
            if (randomSequenceLimit > 1) {
                sequence = RandomUtil.randomLong(randomSequenceLimit);
            } else {
                sequence = 0L;
            }
        }
// 4294967295   1753351885  219169010430   1500000000  14026825184 - 585937500 = 13,440,887,684
        lastTimestamp = timestamp;
        return (((timestamp - twepoch) << TIMESTAMP_LEFT_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence) * 10 + RandomUtil.randomLong(10);

    }

    /**
     * 下一个ID（字符串形式）
     *
     * @return ID 字符串形式
     */
    public String nextIdStr() {
        return Long.toString(nextId());
    }

    // ------------------------------------------------------------------------------------------------------------------------------------ Private method start

    /**
     * 循环等待下一个时间
     *
     * @param lastTimestamp 上次记录的时间
     * @return 下一个时间
     */
    private long tilNextMillis(long lastTimestamp) {
        System.out.println("===============");
        long timestamp = genTime();
        // 循环直到操作系统时间戳变化
        while (timestamp == lastTimestamp) {
            timestamp = genTime();
        }
        if (timestamp < lastTimestamp) {
            // 如果发现新的时间戳比上次记录的时间戳数值小，说明操作系统时间发生了倒退，报错
            throw new IllegalStateException(
                    StrUtil.format("Clock moved backwards. Refusing to generate id for {}ms", lastTimestamp - timestamp));
        }
        return timestamp;
    }

    /**
     * 生成时间戳
     *
     * @return 时间戳
     */
    private long genTime() {
        return (this.useSystemClock ? SystemClock.now() : System.currentTimeMillis()) / TWEPOCH_TIME_UNIT;
    }
    // ------------------------------------------------------------------------------------------------------------------------------------ Private method end

    public static void main(String[] args) {
        for (int i = 0; i < 64; i++) {
            System.out.println(Singleton.get(ShortSnowflake.class).nextId());
        }
    }
}
