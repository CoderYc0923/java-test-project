package com.sky.takeout.system.pay;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.pay.port.PayAttemptPort;
import com.sky.takeout.pojo.entity.PayAttempt;
import com.sky.takeout.pojo.enums.PayAttemptStatus;
import com.sky.takeout.system.mapper.PayAttemptMapper;

@Component
public class PayAttemptPortImpl implements PayAttemptPort {

    private final PayAttemptMapper payAttemptMapper;

    public PayAttemptPortImpl(PayAttemptMapper payAttemptMapper) {
        this.payAttemptMapper = payAttemptMapper;
    }

    /**
     * 按渠道商户单号查一条支付尝试。
     * <p>
     * 回调里 {@code MockPayNotifyDTO.orderNumber} 语义就是 {@code out_trade_no}，
     * 必须用本方法定位到 attempt，再拿到业务 {@code orderId}。
     * <p>
     * SQL 等价：
     * {@code SELECT * FROM pay_attempt WHERE out_trade_no = ? LIMIT 1}
     */
    @Override
    public PayAttempt findByOutTradeNo(String outTradeNo) {
        if(!StringUtils.hasText(outTradeNo)) {
            return null;
        }

        return payAttemptMapper.selectOne(new LambdaQueryWrapper<PayAttempt>().eq(PayAttempt::getOutTradeNo, outTradeNo).last("LIMIT 1"));
    }

    /**
     * 查某业务单当前「进行中」的支付尝试（同一时刻最多一条）。
     * <p>
     * 依赖表约束 {@code uk_order_paying(order_id, paying_flag)}：
     * 只有 PAYING 时 {@code paying_flag = 1}，其它状态为 NULL。
     * {@code requestPay} 复用逻辑会先调本方法。
     * <p>
     * SQL 等价：
     * {@code SELECT * FROM pay_attempt WHERE order_id = ? AND status = 'PAYING' LIMIT 1}
     */
    @Override
    public PayAttempt findPayingByOrderId(Long orderId) {
        if(orderId == null) {
            return null;
        }

        return payAttemptMapper.selectOne(
          new LambdaQueryWrapper<PayAttempt>()
          .eq(PayAttempt::getOrderId, orderId)
          .eq(PayAttempt::getStatus, PayAttemptStatus.PAYING)
          .last("LIMIT 1")
        );
    }

    /**
     * 列出某业务单下全部支付尝试（含历史 CLOSED / SUCCESS / REFUNDED）升序排列。
     * <p>
     * 入账成功后 {@code closeOtherUnpaidAttempts} 会用来遍历并关渠道未付单。
     * 无结果返回空列表，避免调用方 NPE。
     */
    @Override
    public List<PayAttempt> listByOrderId(Long orderId) {
        if (orderId == null) {
            return Collections.emptyList();
        }

        List<PayAttempt> list = payAttemptMapper.selectList(
            new LambdaQueryWrapper<PayAttempt>()
            .eq(PayAttempt::getOrderId, orderId)
            .orderByAsc(PayAttempt::getId)
        );

        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 插入一条「进行中」支付尝试。
     * <p>
     * 调用方应已填好 orderId / orderNumber / outTradeNo / amount 等；
     * 这里再强制校正 status=PAYING、payingFlag=1，防止漏填导致唯一约束失效。
     * <p>
     * 若同单已有 PAYING（paying_flag=1），会触发
     * {@code DuplicateKeyException}（uk_order_paying），由网关捕获后走「复用」分支。
     *
     * @return 影响行数，正常为 1
     */
    @Override
    public int insertPaying(PayAttempt payAttempt) {
        if (payAttempt == null) {
            return 0;
        }

        payAttempt.setStatus(PayAttemptStatus.PAYING);
        payAttempt.setPayingFlag(1);

        return payAttemptMapper.insert(payAttempt);
    }

    /**
     * 带期望旧状态的状态迁移（CAS）。
     * <p>
     * 只有当前库中 {@code status == statusFrom} 时才更新为 {@code statusTo}，
     * 并写入新的 {@code payingFlag}（离开 PAYING 时传 null，释放「进行中」坑位）。
     * <p>
     * 典型用法：
     * <ul>
     *   <li>PAYING → SUCCESS，payingFlag=null（入账成功）</li>
     *   <li>PAYING → CLOSED，payingFlag=null（关单）</li>
     *   <li>任意未终态 → REFUNDING，再 REFUNDING → REFUNDED</li>
     * </ul>
     * <p>
     * 注意：payingFlag 为 null 时也必须写进 UPDATE。
     * 用实体 {@code setPayingFlag(null)} + {@code update(entity, wrapper)} 时，
     * MyBatis-Plus 默认常会跳过 null 字段，因此这里用 {@link LambdaUpdateWrapper#set}。
     * <p>
     * SQL 等价：
     * {@code UPDATE pay_attempt SET status=?, paying_flag=? WHERE id=? AND status=?}
     *
     * @return 影响行数：1=成功；0=状态已变 / 行不存在
     */
    @Override
    public int updateStatus(Long id, PayAttemptStatus statusFrom,PayAttemptStatus statusTo, Integer payingFlag) {
        if (id == null || statusFrom == null || statusTo == null) {
            return 0;
        }

        LambdaUpdateWrapper<PayAttempt> wrapper = new LambdaUpdateWrapper<PayAttempt>()
            .eq(PayAttempt::getId, id)
            .eq(PayAttempt::getStatus, statusFrom)
            .set(PayAttempt::getStatus, statusTo)
            .set(PayAttempt::getPayingFlag, payingFlag);

        // update(null, wrapper) 中的null是占位符，表示不更新任何字段， 
        return payAttemptMapper.update(null, wrapper);
    }

    /**
     * 写回微信返回的 prepay_id（不影响状态机）。
     * <p>
     * native 下单成功后调用；仅按主键更新，不校验 status
     * （复用 PAYING 单时也会再次拿到 prepayId）。
     */
    @Override
    public int updatePrepayId(Long id, String prepayId) {
        if (id == null || !StringUtils.hasText(prepayId)) {
            return 0;
        }

        LambdaUpdateWrapper<PayAttempt> wrapper = new LambdaUpdateWrapper<PayAttempt>()
            .eq(PayAttempt::getId, id)
            .set(PayAttempt::getPrepayId, prepayId);

        return payAttemptMapper.update(null, wrapper);
    }

}
