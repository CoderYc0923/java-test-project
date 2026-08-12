package com.sky.takeout.mockwechat.api;

import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.takeout.mockwechat.api.dto.NativePayRequest;
import com.sky.takeout.mockwechat.api.dto.RefundRequest;
import com.sky.takeout.mockwechat.api.dto.TransactionResponse;
import com.sky.takeout.mockwechat.service.TradeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/v3/pay/transactions")
@Validated
public class TransactionController {

    private final TradeService tradeService;

    public TransactionController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping("/native")
    public TransactionResponse nativePay(@Valid @RequestBody NativePayRequest request) {
        return tradeService.createNative(request);
    }

    @GetMapping("/out-trade-no/{out_trade_no}")
    public TransactionResponse queryByOutTradeNo(@PathVariable("out_trade_no") String outTradeNo) {
        return tradeService.queryByOutTradeNo(outTradeNo);
    }

    /**
     * 关单：NOTPAY → CLOSED。
     * POST /v3/pay/transactions/out-trade-no/{out_trade_no}/close
     */
    @PostMapping("/out-trade-no/{out_trade_no}/close")
    public TransactionResponse close(@PathVariable("out_trade_no") String outTradeNo) {
        return tradeService.close(outTradeNo);
    }

    /**
     * 退款（教学简化）：SUCCESS → REFUND。
     * POST /v3/pay/transactions/out-trade-no/{out_trade_no}/refund
     */
    @PostMapping("/out-trade-no/{out_trade_no}/refund")
    public TransactionResponse refund(
            @PathVariable("out_trade_no") String outTradeNo,
            @RequestBody(required = false) RefundRequest body) {
        String reason = body != null && StringUtils.hasText(body.getReason())
                ? body.getReason()
                : "duplicate_pay";
        return tradeService.refund(outTradeNo, reason);
    }
}
