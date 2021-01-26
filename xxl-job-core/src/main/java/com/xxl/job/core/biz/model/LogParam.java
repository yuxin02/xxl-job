package com.xxl.job.core.biz.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author xuxueli 2020-04-11 22:27
 */
@Data
public class LogParam implements Serializable {
    private static final long serialVersionUID = 42L;

    public LogParam() {
    }
    public LogParam(long logDateTim, long logId, int fromLineNum) {
        this.logDateTim = logDateTim;
        this.logId = logId;
        this.fromLineNum = fromLineNum;
    }

    private long logDateTim;
    private long logId;
    private int fromLineNum;

}