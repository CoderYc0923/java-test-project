package com.sky.takeout.pay.client;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.sky.takeout.common.exception.BusinessException;
import com.sky.takeout.common.result.ErrorCode;
import com.sky.takeout.pay.client.dto.NativePayRequest;
import com.sky.takeout.pay.client.dto.TransactionResponse;
import com.sky.takeout.pay.config.PayProperties;
import com.sky.takeout.pojo.entity.Order;


/**
 * 微信支付 Mock 实现
 * <p>
 * 真实项目里这里会是官方 SDK 或自签 RSA 的 HTTP 调用；
 * 本类对应 API.md：POST /v3/pay/transactions/native 。
 * <p>
 * 注意：本类<strong>不</strong>直接改订单支付状态；付成功只认商户 notify。
 * 
 * MockWechatHttpClient
 */
@Component
public class MockWechatHttpClient {

    private final RestClient.Builder payRestClientBuilder;

    private final RestClient restClient;

    private final PayProperties payProperties;

    private final Logger log = LoggerFactory.getLogger(MockWechatHttpClient.class);

    public MockWechatHttpClient(RestClient.Builder payRestClientBuilder, PayProperties payProperties) {
        this.payProperties = payProperties;

        // baseUrl 来自配置，便于换环境
        String base = trimtrailingSlash(payProperties.getMockWechatBaseUrl());
        this.payRestClientBuilder = payRestClientBuilder.baseUrl(base);
        this.restClient = this.payRestClientBuilder.build();
    }

    /**
     * 创建微信支付请求
     * @param outTradeNo 商户订单号
     * @param amount 金额
     * @param description 描述
     * @return
     */
    public TransactionResponse createNativePay(String outTradeNo, BigDecimal amount, String description) {
        if (!StringUtils.hasText(outTradeNo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "outTradeNo 不能为空");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "金额非法");
        }
        if (!StringUtils.hasText(payProperties.getMerchantNotifyUrl())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "notifyUrl 未配置");
        }

        /**
         * 构建请求体
         */
        NativePayRequest body = NativePayRequest.builder()
            .outTradeNo(outTradeNo)
            .description(description)
            .notifyUrl(payProperties.getMerchantNotifyUrl())
            .amount(amount)
            .build();

        log.info("创建微信支付请求：{}", body);

        try {
            /**
             * 发起 HTTP 请求
             */
            TransactionResponse response = restClient.post()
                .uri("/v3/pay/transactions/native") // url
                .contentType(MediaType.APPLICATION_JSON) // 请求头
                .body(body) // 请求体
                .retrieve() // 发起请求
                // 4xx/5xx 会抛出 RestClientResponseException
                .body(TransactionResponse.class); // 响应体

            /**
             * 处理响应
             */
            if (response == null || !StringUtils.hasText(response.getPrepayId())) {
                throw new BusinessException(ErrorCode.ERROR, "响应缺失 pre_pay_id");
            }

            log.info("创建微信支付请求成功 outTradeNo={}, prePayId={}, tradeType={}", response.getOutTradeNo(), response.getPrepayId(), response.getTradeState());
            return response;

        } catch (RestClientResponseException e) {
            log.error("创建微信支付请求失败 status={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.ERROR, "创建微信支付请求失败: HTTP " + e.getStatusCode().value());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 链接拒绝等
            log.error("创建微信支付请求异常：{}", e.getMessage());
            throw new BusinessException(ErrorCode.ERROR, "创建微信支付请求异常: {}" + e.getMessage());
        }

    }

    /**
     * 创建微信支付请求
     * @param order 订单
     * @param outTradeNo 商户订单号
     * @return
     */
    public TransactionResponse createNativePay(Order order, String outTradeNo) {
        return createNativePay(outTradeNo, order.getAmount(), "外卖订单-" + order.getNumber());
    }

    /**
     * 查询微信支付订单
     * @param outTradeNo 订单号
     * @return
     */
    public TransactionResponse queryByOutTradeNo(String outTradeNo) {
        try {
            return restClient.get()
                .uri("/v3/pay/transactions/out-trade-no/{outTradeNo}", outTradeNo)
                .retrieve()
                .body(TransactionResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("查询微信支付订单失败 status={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.ERROR, outTradeNo + "查询微信支付订单失败");
        }
    }

    /**
     * 关单：渠道 NOTPAY → CLOSED。
     * POST /v3/pay/transactions/out-trade-no/{outTradeNo}/close
     */
    public TransactionResponse close(String outTradeNo) {
        if (!StringUtils.hasText(outTradeNo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "outTradeNo 不能为空");
        }
        try {
            return restClient.post()
                    .uri("/v3/pay/transactions/out-trade-no/{outTradeNo}/close", outTradeNo)
                    .retrieve()
                    .body(TransactionResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("关单失败 outTradeNo={} status={} body={}",
                    outTradeNo, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.ERROR,
                    "假微信关单失败: HTTP " + e.getStatusCode().value());
        }
    }

    /**
     * 退款（教学简化）：渠道 SUCCESS → REFUND。
     * POST /v3/pay/transactions/out-trade-no/{outTradeNo}/refund
     */
    public TransactionResponse refund(String outTradeNo, String reason) {
        if (!StringUtils.hasText(outTradeNo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "outTradeNo 不能为空");
        }
        String r = StringUtils.hasText(reason) ? reason : "duplicate_pay";
        try {
            return restClient.post()
                    .uri("/v3/pay/transactions/out-trade-no/{outTradeNo}/refund", outTradeNo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("reason", r))
                    .retrieve()
                    .body(TransactionResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("退款失败 outTradeNo={} status={} body={}",
                    outTradeNo, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.ERROR,
                    "假微信退款失败: HTTP " + e.getStatusCode().value());
        }
    }

    /**
     * 去除 URL 末尾的斜杠
     * @param url
     * @return
     */
    private String trimtrailingSlash(String url) {
        // 若 URL 为空，则返回默认值
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:9090";
        }
        // 若末尾有斜杠，则去除
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
