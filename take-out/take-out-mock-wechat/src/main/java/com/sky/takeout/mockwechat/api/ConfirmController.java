package com.sky.takeout.mockwechat.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sky.takeout.mockwechat.api.dto.ConfirmRequest;
import com.sky.takeout.mockwechat.api.dto.ConfirmResponse;
import com.sky.takeout.mockwechat.service.TradeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/mock/pay")
@Validated
public class ConfirmController {

    private final TradeService tradeService;

    public ConfirmController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping("/confirm")
    public ConfirmResponse confirm(@Valid @RequestBody ConfirmRequest request) {
        return tradeService.confirm(request);
    }
}
