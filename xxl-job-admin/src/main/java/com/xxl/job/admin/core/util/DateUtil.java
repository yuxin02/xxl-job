package com.xxl.job.admin.core.util;

import java.util.Calendar;
import java.util.Date;

/**
 * Description:
 *
 * @author chenweibing
 * @date 2021-01-22 21:21
 */
public class DateUtil {
    public static Date todayFrom(Calendar itemDay) {
        itemDay.set(Calendar.HOUR_OF_DAY, 0);
        itemDay.set(Calendar.MINUTE, 0);
        itemDay.set(Calendar.SECOND, 0);
        itemDay.set(Calendar.MILLISECOND, 0);

        return itemDay.getTime();

    }

    public static Date getDay(Calendar itemDay) {
        itemDay.set(Calendar.HOUR_OF_DAY, 0);
        itemDay.set(Calendar.MINUTE, 0);
        itemDay.set(Calendar.SECOND, 0);
        itemDay.set(Calendar.MILLISECOND, 0);

        return itemDay.getTime();

    }

    public static Date todayTo(Calendar itemDay) {
        itemDay.set(Calendar.HOUR_OF_DAY, 23);
        itemDay.set(Calendar.MINUTE, 59);
        itemDay.set(Calendar.SECOND, 59);
        itemDay.set(Calendar.MILLISECOND, 999);
        return itemDay.getTime();
    }
}
