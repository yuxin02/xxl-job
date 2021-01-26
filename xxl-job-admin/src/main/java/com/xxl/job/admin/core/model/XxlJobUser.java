package com.xxl.job.admin.core.model;

import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * @author xuxueli 2019-05-04 16:43:12
 */
@Data
public class XxlJobUser {
    private int id;
    /**
     * 账号
     */
    private String username;
    /**
     * 密码
     */
    private String password;
    /**
     * 角色：0-普通用户、1-管理员
     */
    private int role;
    /**
     * 权限：执行器ID列表，多个逗号分割
     */
    private String permission;

    /**
     * plugin
     */
    public boolean validPermission(int jobGroup) {
        if (this.role == 1) {
            return true;
        }
        if (StringUtils.hasText(this.permission)) {
            for (String permissionItem : this.permission.split(",")) {
                if (String.valueOf(jobGroup).equals(permissionItem)) {
                    return true;
                }
            }
        }
        return false;
    }
}
