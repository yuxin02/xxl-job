package com.xxl.job.core.context;

import lombok.Data;

/**
 * xxl-job context
 *
 * @author xuxueli 2020-05-21
 * [Dear hj]
 */
@Data
public class XxlJobContext {

    public static final int HANDLE_CODE_SUCCESS = 200;
    public static final int HANDLE_CODE_FAIL = 500;
    public static final int HANDLE_CODE_TIMEOUT = 502;

    // ---------------------- base info ----------------------

    private final long jobId;
    private final String jobParam;

    // ---------------------- for log ----------------------
    private final String jobLogFileName;

    // ---------------------- for shard ----------------------
    private final int shardIndex;
    private final int shardTotal;

    // ---------------------- for handle ----------------------

    /**
     * handleCode：The result status of job execution
     * 200 : success
     * 500 : fail
     * 502 : timeout
     */
    private int handleCode;

    /**
     * handleMsg：The simple log msg of job execution
     */
    private String handleMsg;


    public XxlJobContext(long jobId, String jobParam, String jobLogFileName, int shardIndex, int shardTotal) {
        this.jobId = jobId;
        this.jobParam = jobParam;
        this.jobLogFileName = jobLogFileName;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        // default success
        this.handleCode = HANDLE_CODE_SUCCESS;
    }


    // ---------------------- tool ----------------------

    // support for child thread of job handler)
    private static InheritableThreadLocal<XxlJobContext> contextHolder = new InheritableThreadLocal();

    public static void setXxlJobContext(XxlJobContext xxlJobContext) {
        contextHolder.set(xxlJobContext);
    }

    public static XxlJobContext getXxlJobContext() {
        return contextHolder.get();
    }

}