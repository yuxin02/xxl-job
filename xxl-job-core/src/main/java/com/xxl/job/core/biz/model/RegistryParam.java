package com.xxl.job.core.biz.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Created by xuxueli on 2017-05-10 20:22:42
 */
@Data
public class RegistryParam implements Serializable {
    private static final long serialVersionUID = 42L;

    private String registryGroup;
    private String registryKey;
    private String registryValue;

    public RegistryParam() {
    }

    public RegistryParam(String registryGroup, String registryKey, String registryValue) {
        this.registryGroup = registryGroup;
        this.registryKey = registryKey;
        this.registryValue = registryValue;
    }
}
