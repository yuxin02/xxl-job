package com.xxl.job.core.biz.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author xuxueli 2020-04-11 22:27
 */
@Data
public class IdleBeatParam implements Serializable {
    private static final long serialVersionUID = 42L;

    public IdleBeatParam() {
    }
    public IdleBeatParam(int jobId) {
        this.jobId = jobId;
    }

    private int jobId;
}