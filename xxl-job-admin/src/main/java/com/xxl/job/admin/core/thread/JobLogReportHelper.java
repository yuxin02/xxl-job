package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobLogReport;
import com.xxl.job.admin.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * job log report helper
 *
 * @author xuxueli 2019-11-22
 */
public class JobLogReportHelper {
    private static Logger logger = LoggerFactory.getLogger(JobLogReportHelper.class);

    private static JobLogReportHelper instance = new JobLogReportHelper();
    public static JobLogReportHelper getInstance() {
        return instance;
    }

    private Thread logrThread;
    private volatile boolean toStop = false;


    public void start() {
        logrThread = new Thread(() -> {
            // last clean log time
            long lastCleanLogTime = 0;
            while (!toStop) {
                // 1、log-report refresh: refresh log report in 3 days
                try {
                    refreshLogs();
                } catch (Exception e) {
                    if (!toStop) {
                        logger.error(">>>>>>>>>>> xxl-job, job log report thread error:{}", e);
                    }
                }

                // 2、log-clean: switch open & once each day
                lastCleanLogTime = cleanLogs(lastCleanLogTime);

                try {
                    TimeUnit.MINUTES.sleep(1);
                } catch (Exception e) {
                    if (!toStop) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }

            logger.info(">>>>>>>>>>> xxl-job, job log report thread stop");

        }, "xxl-job, admin JobLogReportHelper");
        logrThread.setDaemon(true);
        logrThread.start();
    }

    public void toStop() {
        toStop = true;
        // interrupt and wait
        logrThread.interrupt();
        try {
            logrThread.join();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void saveOrUpdate(XxlJobLogReport xxlJobLogReport) {
        int ret = XxlJobAdminConfig.getAdminConfig().getXxlJobLogReportDao().update(xxlJobLogReport);
        if (ret < 1) {
            XxlJobAdminConfig.getAdminConfig().getXxlJobLogReportDao().save(xxlJobLogReport);
        }
    }

    private void refreshLogs() {
        for (int i = 0; i < 3; i++) {
            // today
            Calendar itemDay = Calendar.getInstance();
            itemDay.add(Calendar.DAY_OF_MONTH, -i);

            Date todayFrom = DateUtil.todayFrom(itemDay);
            Date todayTo = DateUtil.todayTo(itemDay);

            // refresh log-report every minute
            XxlJobLogReport xxlJobLogReport = new XxlJobLogReport()
                    .setTriggerDay(todayFrom)
                    .setRunningCount(0)
                    .setSucCount(0)
                    .setFailCount(0);

            Map<String, Object> triggerCountMap = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().findLogReport(todayFrom, todayTo);
            if (triggerCountMap != null && triggerCountMap.size() > 0) {
                int triggerDayCount = object2Int(triggerCountMap.get("triggerDayCount"));
                int triggerDayCountRunning = object2Int(triggerCountMap.get("triggerDayCountRunning"));
                int triggerDayCountSuc = object2Int(triggerCountMap.get("triggerDayCountSuc"));
                int triggerDayCountFail = triggerDayCount - triggerDayCountRunning - triggerDayCountSuc;

                xxlJobLogReport.setRunningCount(triggerDayCountRunning)
                        .setSucCount(triggerDayCountSuc)
                        .setFailCount(triggerDayCountFail);
            }

            // do refresh
            saveOrUpdate(xxlJobLogReport);
        }

    }

    private long cleanLogs(long lastCleanLogTime) {
        if (XxlJobAdminConfig.getAdminConfig().getLogretentiondays() > 0
                && System.currentTimeMillis() - lastCleanLogTime > TimeUnit.DAYS.toMillis(1)) {

            // expire-time
            Calendar expiredDay = Calendar.getInstance();
            expiredDay.add(Calendar.DAY_OF_MONTH, -1 * XxlJobAdminConfig.getAdminConfig().getLogretentiondays());
            Date clearBeforeTime = DateUtil.getDay(expiredDay);

            // clean expired log
            List<Long> logIds = null;
            do {
                logIds = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().findClearLogIds(0, 0, clearBeforeTime, 0, 1000);
                if (logIds != null && logIds.size() > 0) {
                    XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().clearLog(logIds);
                }
            } while (logIds != null && logIds.size() > 0);

            // update clean time
            return System.currentTimeMillis();
        }
        return lastCleanLogTime;
    }

    private int object2Int(Object object) {
        return object == null ? 0 : Integer.parseInt(String.valueOf(object));
    }


    public static void main(String[] args) {
        for (int i = 0; i < 3; i++) {
            // today
            Calendar itemDay = Calendar.getInstance();
            itemDay.add(Calendar.DAY_OF_MONTH, -i);

            Date todayFrom = DateUtil.todayFrom(itemDay);


            Date todayTo = DateUtil.todayTo(itemDay);

            System.out.println("Today from : " + todayFrom + ", to : " + todayTo);
        }
    }

}
