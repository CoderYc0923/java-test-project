package com.sky.takeout.pojo.vo.order;

import lombok.Data;

@Data
public final class OrderStatisticsVO {

    /** status = 2 待接单 */
    private Integer toBeConfirmed;

    /** status = 3 待派送（已接单） */
    private Integer confirmed;

    /** status = 4 派送中 */
    private Integer deliveryInProgress;
}
