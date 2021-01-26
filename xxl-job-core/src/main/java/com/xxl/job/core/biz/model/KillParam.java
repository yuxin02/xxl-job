package com.xxl.job.core.biz.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author xuxueli 2020-04-11 22:27
 */
@Data
public class KillParam implements Serializable {
    private static final long serialVersionUID = 42L;

    public KillParam() {
    }
    public KillParam(int jobId) {
        this.jobId = jobId;
    }

    private int jobId;
}