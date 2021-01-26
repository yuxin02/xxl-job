package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.cron.CronExpression;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.scheduler.MisfireStrategyEnum;
import com.xxl.job.admin.core.scheduler.ScheduleTypeEnum;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author xuxueli 2019-05-21
 */
public class JobScheduleHelper {
    private static Logger logger = LoggerFactory.getLogger(JobScheduleHelper.class);

    /**
     * 单例
     */
    private JobScheduleHelper() { }
    private static JobScheduleHelper instance = new JobScheduleHelper();
    public static JobScheduleHelper getInstance() {
        return instance;
    }

    /**
     * 预取的时间段
     */
    public static final long PRE_READ_MS = 5000;
    /**
     * 定了两个线程对象：
     * 1. scheduleThread 负责从数据库读取任务，然后放入ringData
     * 2. ringThread 执行线程，定期从ringData获取数据，然后放入线程池执行
     */
    private Thread scheduleThread;
    private Thread ringThread;
    private volatile boolean scheduleThreadToStop = false;
    private volatile boolean ringThreadToStop = false;
    /**
     * 待处理的任务列表，key对应tick，预取时计算tick值
     */
    private volatile static Map<Integer, List<Integer>> ringData = new ConcurrentHashMap<>();


    private static class DBLock {
        private Connection conn = null;
        private Boolean connAutoCommit = null;
        private PreparedStatement preparedStatement = null;

        public DBLock() throws SQLException {
            conn = XxlJobAdminConfig.getAdminConfig().getDataSource().getConnection();
            connAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
        }

        public void lock() throws SQLException {
            // TODO: 这里通过数据库'for update'抢占一个分布式锁
            preparedStatement = conn.prepareStatement("select 1 from xxl_job_lock where lock_name = 'schedule_lock' for update");
            preparedStatement.execute();
        }

        public void unlock() {
            // commit
            if (conn != null) {
                try {
                    conn.commit();
                } catch (SQLException e) {
                }
                try {
                    conn.setAutoCommit(connAutoCommit);
                } catch (SQLException e) {
                }
                try {
                    conn.close();
                } catch (SQLException e) {
                }
            }

            // close PreparedStatement
            if (null != preparedStatement) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                }
            }
        }
    }

    private boolean dealPreReadJobs() throws Exception {
        boolean preReadSuc = true;
        // 预取的任务数 pre-read count: treadpool-size * trigger-qps (each trigger cost 50ms, qps = 1000/50 = 20)
        int preReadCount = (XxlJobAdminConfig.getAdminConfig().getTriggerPoolFastMax() + XxlJobAdminConfig.getAdminConfig().getTriggerPoolSlowMax()) * 20;

        // 1、pre read
        long nowTime = System.currentTimeMillis();
        List<XxlJobInfo> scheduleList = XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().scheduleJobQuery(nowTime + PRE_READ_MS, preReadCount);
        preReadSuc = scheduleList != null && scheduleList.size() > 0;
        if (preReadSuc) {
            logger.info(">>>>>>>>>>> xxl-job, job scheduler, pre read count : " + scheduleList.size());
            // 2、push time-ring
            for (XxlJobInfo jobInfo : scheduleList) {
                // time-ring jump
                if (nowTime > jobInfo.getTriggerNextTime() + PRE_READ_MS) {
                    // 2.1、trigger-expire > 5s：pass && make next-trigger-time
                    // 1、misfire match
                    MisfireStrategyEnum misfireStrategyEnum = MisfireStrategyEnum.match(jobInfo.getMisfireStrategy(), MisfireStrategyEnum.DO_NOTHING);
                    if (MisfireStrategyEnum.FIRE_ONCE_NOW == misfireStrategyEnum) {
                        // FIRE_ONCE_NOW 》 trigger
                        JobTriggerPoolHelper.trigger(jobInfo.getId(), TriggerTypeEnum.MISFIRE, -1, null, null, null);
                    }

                    // 2、fresh next
                    refreshNextValidTime(jobInfo, new Date());
                } else if (nowTime > jobInfo.getTriggerNextTime()) {
                    // 2.2、trigger-expire < 5s：direct-trigger && make next-trigger-time
                    // 1、trigger
                    JobTriggerPoolHelper.trigger(jobInfo.getId(), TriggerTypeEnum.CRON, -1, null, null, null);
                    // 2、fresh next
                    refreshNextValidTime(jobInfo, new Date());

                    // next-trigger-time in 5s, pre-read again
                    if (jobInfo.getTriggerStatus() == 1 && nowTime + PRE_READ_MS > jobInfo.getTriggerNextTime()) {
                        pushJob(jobInfo);
                    }
                } else {
                    // 2.3、trigger-pre-read：time-ring trigger && make next-trigger-time
                    pushJob(jobInfo);
                }
            }

            // 3、update trigger info
            for (XxlJobInfo jobInfo : scheduleList) {
                XxlJobAdminConfig.getAdminConfig().getXxlJobInfoDao().scheduleUpdate(jobInfo);
            }
        }

        return preReadSuc;
    }

    private void pushJob(XxlJobInfo jobInfo) throws Exception {
        // 1、make ring second
        int ringSecond = (int) ((jobInfo.getTriggerNextTime() / 1000) % 60);
        // 2、push time ring
        pushTimeRing(ringSecond, jobInfo.getId());
        // 3、fresh next
        refreshNextValidTime(jobInfo, new Date(jobInfo.getTriggerNextTime()));
    }

    private void refreshNextValidTime(XxlJobInfo jobInfo, Date fromTime) throws Exception {
        Date nextValidTime = generateNextValidTime(jobInfo, fromTime);
        if (nextValidTime != null) {
            jobInfo.setTriggerLastTime(jobInfo.getTriggerNextTime());
            jobInfo.setTriggerNextTime(nextValidTime.getTime());
        } else {
            jobInfo.setTriggerStatus(0);
            jobInfo.setTriggerLastTime(0);
            jobInfo.setTriggerNextTime(0);
        }
    }

    private void pushTimeRing(int ringSecond, int jobId) {
        // push async ring
        List<Integer> ringItemData = ringData.get(ringSecond);
        if (ringItemData == null) {
            ringItemData = new ArrayList<>();
            ringData.put(ringSecond, ringItemData);
        }
        ringItemData.add(jobId);

    }

    public void toStop() {
        // 1、stop schedule
        scheduleThreadToStop = true;
        try {
            TimeUnit.SECONDS.sleep(1);  // wait
        } catch (InterruptedException e) {
        }
        if (scheduleThread.getState() != Thread.State.TERMINATED) {
            // interrupt and wait
            scheduleThread.interrupt();
            try {
                scheduleThread.join();
            } catch (InterruptedException e) {
            }
        }

        // if has ring data
        boolean hasRingData = false;
        if (!ringData.isEmpty()) {
            for (int second : ringData.keySet()) {
                List<Integer> tmpData = ringData.get(second);
                if (tmpData != null && tmpData.size() > 0) {
                    hasRingData = true;
                    break;
                }
            }
        }
        if (hasRingData) {
            try {
                TimeUnit.SECONDS.sleep(8);
            } catch (InterruptedException e) {
            }
        }

        // stop ring (wait job-in-memory stop)
        ringThreadToStop = true;
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
        }
        if (ringThread.getState() != Thread.State.TERMINATED) {
            // interrupt and wait
            ringThread.interrupt();
            try {
                ringThread.join();
            } catch (InterruptedException e) {
            }
        }
    }

    public void start() {
        // schedule thread
        scheduleThread = new Thread(() -> {
            // TODO: 为什么上来就睡眠，对齐？？？
            try {
                TimeUnit.MILLISECONDS.sleep(5000 - System.currentTimeMillis() % 1000);
            } catch (InterruptedException e) {
            }

            while (!scheduleThreadToStop) {
                // Scan Job
                long start = System.currentTimeMillis();

                DBLock lock = null;
                boolean preReadSuc = true;
                try {
                    // tx start
                    lock = new DBLock();
                    lock.lock();
                    preReadSuc = dealPreReadJobs();
                    // tx stop
                } catch (Exception e) {
                } finally {
                    if (lock != null) {
                        lock.unlock();
                    }
                }
                long cost = System.currentTimeMillis() - start;


                // Wait seconds, align second
                if (cost < 1000) {  // scan-overtime, not wait
                    try {
                        // pre-read period: success > scan each second; fail > skip this period;
                        TimeUnit.MILLISECONDS.sleep((preReadSuc ? 1000 : PRE_READ_MS) - System.currentTimeMillis() % 1000);
                    } catch (InterruptedException e) {
                    }
                }

            }
        }, "xxl-job, admin JobScheduleHelper#scheduleThread");
        scheduleThread.setDaemon(true);
        scheduleThread.start();


        // ring thread
        ringThread = new Thread(() -> {

            // align second
            try {
                TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
            } catch (InterruptedException e) {
            }

            while (!ringThreadToStop) {

                try {
                    // second data
                    List<Integer> ringItemData = new ArrayList<>();
                    int nowSecond = Calendar.getInstance().get(Calendar.SECOND);   // 避免处理耗时太长，跨过刻度，向前校验一个刻度；
                    for (int i = 0; i < 2; i++) {
                        List<Integer> tmpData = ringData.remove((nowSecond + 60 - i) % 60);
                        if (tmpData != null) {
                            ringItemData.addAll(tmpData);
                        }
                    }

                    // ring trigger
                    if (ringItemData.size() > 0) {
                        for (int jobId : ringItemData) {
                            JobTriggerPoolHelper.trigger(jobId, TriggerTypeEnum.CRON, -1, null, null, null);
                        }
                        ringItemData.clear();
                    }
                } catch (Exception e) {
                }

                // next second, align second
                try {
                    TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
                } catch (InterruptedException e) {
                }
            }
        }, "xxl-job, admin JobScheduleHelper#ringThread");
        ringThread.setDaemon(true);
        ringThread.start();
    }

    // ---------------------- tools ----------------------
    public static Date generateNextValidTime(XxlJobInfo jobInfo, Date fromTime) throws Exception {
        ScheduleTypeEnum scheduleTypeEnum = ScheduleTypeEnum.match(jobInfo.getScheduleType(), null);
        if (ScheduleTypeEnum.CRON == scheduleTypeEnum) {
            Date nextValidTime = new CronExpression(jobInfo.getScheduleConf()).getNextValidTimeAfter(fromTime);
            return nextValidTime;
        } else if (ScheduleTypeEnum.FIX_RATE == scheduleTypeEnum /*|| ScheduleTypeEnum.FIX_DELAY == scheduleTypeEnum*/) {
            return new Date(fromTime.getTime() + Integer.valueOf(jobInfo.getScheduleConf()) * 1000);
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            TimeUnit.MILLISECONDS.sleep(1000 - System.currentTimeMillis() % 1000);
        } catch (InterruptedException e) {
        }
        for (int i = 0; i < 10; i++) {
            int nowSecond = Calendar.getInstance().get(Calendar.SECOND);
            System.out.println(nowSecond + ", " + (nowSecond + 60) % 60 + ", " + (nowSecond + 60 - 1) % 60);
            try {
                TimeUnit.MILLISECONDS.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }
}
