package com.xxl.job.executor.service.jobhandler;

import com.xxl.job.core.handler.IJobHandler;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Description:
 *
 * @author chenweibing
 * @date 2021-01-21 12:05
 */
@Component
public class HelloJobHandler  extends IJobHandler {
    @Override
    public void execute() throws Exception {
        String now = new Date().toString();
        System.out.println(now + "XXL-JOB, Hello World.");
    }
}
