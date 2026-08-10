package com.sky.takeout.mockwechat.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.takeout.mockwechat.api.dto.NativePayRequest;
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
}
