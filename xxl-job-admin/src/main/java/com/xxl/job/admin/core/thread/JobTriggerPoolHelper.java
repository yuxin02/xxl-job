package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.xxl.job.admin.core.trigger.XxlJobTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * job trigger thread pool helper
 *
 * @author xuxueli 2018-07-03 21:08:07
 */
public class JobTriggerPoolHelper {
    private static Logger logger = LoggerFactory.getLogger(JobTriggerPoolHelper.class);

    private static JobTriggerPoolHelper helper = new JobTriggerPoolHelper();

    /** ms -> min */
    private volatile long minTim = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis());

    /** job timeout count */
    private volatile ConcurrentMap<Integer, AtomicInteger> jobTimeoutCountMap = new ConcurrentHashMap<>();

    // ---------------------- trigger pool ----------------------
    /** 定两个线程池 fast/slow */
    private ThreadPoolExecutor fastTriggerPool = null;
    private ThreadPoolExecutor slowTriggerPool = null;

    private void start() {
        fastTriggerPool = new ThreadPoolExecutor(
                10,
                XxlJobAdminConfig.getAdminConfig().getTriggerPoolFastMax(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                (r) -> new Thread(r, "xxl-job, admin JobTriggerPoolHelper-fastTriggerPool-" + r.hashCode())

        );
        slowTriggerPool = new ThreadPoolExecutor(
                10,
                XxlJobAdminConfig.getAdminConfig().getTriggerPoolSlowMax(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2000),
                (r) -> new Thread(r, "xxl-job, admin JobTriggerPoolHelper-slowTriggerPool-" + r.hashCode())
        );
    }

    private void stop() {
        fastTriggerPool.shutdownNow();
        slowTriggerPool.shutdownNow();
        logger.info(">>>>>>>>> xxl-job trigger thread pool shutdown success.");
    }


    /** add trigger */
    private void addTrigger(final int jobId, final TriggerTypeEnum triggerType, final int failRetryCount, final String executorShardingParam, final String executorParam, final String addressList) {
        // choose thread pool
        AtomicInteger jobTimeoutCount = jobTimeoutCountMap.get(jobId);
        // job-timeout 10 times in 1 min
        ThreadPoolExecutor triggerPool_ = (jobTimeoutCount != null && jobTimeoutCount.get() > 10) ? slowTriggerPool : fastTriggerPool;

        // trigger
        triggerPool_.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                // do trigger
                XxlJobTrigger.trigger(jobId, triggerType, failRetryCount, executorShardingParam, executorParam, addressList);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                // check timeout-count-map
                long minTim_now = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis());
                if (minTim != minTim_now) {
                    minTim = minTim_now;
                    jobTimeoutCountMap.clear();
                }

                // incr timeout-count-map
                long cost = System.currentTimeMillis() - start;
                if (cost > 500) {       // ob-timeout threshold 500ms
                    AtomicInteger timeoutCount = jobTimeoutCountMap.putIfAbsent(jobId, new AtomicInteger(1));
                    if (timeoutCount != null) {
                        timeoutCount.incrementAndGet();
                    }
                }
            }
        });
    }


    // ---------------------- helper ----------------------
    public static void toStart() {
        helper.start();
    }
    public static void toStop() {
        helper.stop();
    }

    /**
     * @param jobId
     * @param triggerType
     * @param failRetryCount        >=0: use this param
     *                              <0: use param from job info config
     * @param executorShardingParam
     * @param executorParam         null: use job param
     *                              not null: cover job param
     */
    public static void trigger(int jobId, TriggerTypeEnum triggerType, int failRetryCount, String executorShardingParam, String executorParam, String addressList) {
        helper.addTrigger(jobId, triggerType, failRetryCount, executorShardingParam, executorParam, addressList);
    }



    public static void main(String[] args) {
//        System.out.println((System.currentTimeMillis() / 1000 / 60));
//         ConcurrentMap<Integer, AtomicInteger> timeoutCountMap = new ConcurrentHashMap<>();
//        AtomicInteger timeoutCount = timeoutCountMap.putIfAbsent(1, new AtomicInteger(1));
//        if (timeoutCount != null) {
//            int i = timeoutCount.incrementAndGet();
//            System.out.println(i);
//        }
//         timeoutCount = timeoutCountMap.putIfAbsent(1, new AtomicInteger(1));
//        if (timeoutCount != null) {
//            int i = timeoutCount.incrementAndGet();
//            System.out.println(i);
//        }

        System.out.println(TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis()));
    }
}
