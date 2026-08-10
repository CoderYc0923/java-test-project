package com.sky.takeout.mockwechat.api;

import java.math.BigDecimal;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.sky.takeout.mockwechat.api.dto.TransactionResponse;
import com.sky.takeout.mockwechat.domain.TradeState;
import com.sky.takeout.mockwechat.service.TradeService;

/**
 * 教学用「微信支付确认页」——对应真实场景里用户在微信里点付款。
 * <p>
 * GET /mock/pay/checkout?out_trade_no=ORD...
 */
@Controller
@RequestMapping("/mock/pay")
public class CheckoutPageController {

    private final TradeService tradeService;

    public CheckoutPageController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @GetMapping(value = "/checkout", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String checkout(@RequestParam("out_trade_no") String outTradeNo) {
        if (!StringUtils.hasText(outTradeNo)) {
            return errorPage("缺少 out_trade_no");
        }

        TransactionResponse trade;
        try {
            trade = tradeService.queryByOutTradeNo(outTradeNo.trim());
        } catch (MockWechatException ex) {
            return errorPage(ex.getMessage() != null ? ex.getMessage() : "订单不存在");
        }

        String safeNo = escapeHtml(trade.getOutTradeNo());
        String amount = formatAmount(trade.getAmount());
        String desc = escapeHtml(trade.getDescription() != null ? trade.getDescription() : "外卖订单");
        boolean alreadyPaid = TradeState.SUCCESS.name().equals(trade.getTradeState());
        String state = escapeHtml(trade.getTradeState());
        String btnDisabled = alreadyPaid ? "disabled" : "";
        String jsOutTradeNo = toJsString(trade.getOutTradeNo());
        String jsAlreadyPaid = alreadyPaid ? "true" : "false";

        // 不用 String.formatted：HTML/CSS 里的 %（如 width:60%）会触发格式化异常 → 500
        return PAGE_TEMPLATE
                .replace("{{AMOUNT}}", amount)
                .replace("{{DESC}}", desc)
                .replace("{{OUT_TRADE_NO}}", safeNo)
                .replace("{{STATE}}", state)
                .replace("{{BTN_DISABLED}}", btnDisabled)
                .replace("{{JS_OUT_TRADE_NO}}", jsOutTradeNo)
                .replace("{{JS_ALREADY_PAID}}", jsAlreadyPaid);
    }

    private static final String PAGE_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>模拟微信支付</title>
              <style>
                * { box-sizing: border-box; }
                body {
                  margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  background: #f5f5f5; color: #1a1a1a;
                }
                .wrap { max-width: 420px; margin: 40px auto; padding: 0 16px; }
                .card {
                  background: #fff; border-radius: 12px; padding: 28px 24px 32px;
                  box-shadow: 0 8px 24px rgba(0,0,0,.06);
                }
                .brand { color: #07c160; font-weight: 700; font-size: 18px; margin-bottom: 8px; }
                .sub { color: #888; font-size: 13px; margin-bottom: 24px; }
                .amount { font-size: 36px; font-weight: 700; margin: 8px 0 20px; }
                .amount span { font-size: 18px; margin-right: 4px; }
                .row { display: flex; justify-content: space-between; font-size: 14px;
                       padding: 10px 0; border-top: 1px solid #f0f0f0; color: #555; }
                .row b { color: #222; font-weight: 500; word-break: break-all; text-align: right; max-width: 60%; }
                button {
                  width: 100%; margin-top: 28px; border: 0; border-radius: 8px;
                  background: #07c160; color: #fff; font-size: 16px; font-weight: 600;
                  padding: 14px; cursor: pointer;
                }
                button:disabled { background: #9ad9b5; cursor: not-allowed; }
                .tip { margin-top: 14px; font-size: 12px; color: #999; line-height: 1.5; }
                .ok { color: #07c160; font-weight: 600; margin-top: 16px; }
                .err { color: #e64340; margin-top: 12px; font-size: 13px; }
              </style>
            </head>
            <body>
              <div class="wrap">
                <div class="card">
                  <div class="brand">模拟微信支付</div>
                  <div class="sub">教学沙箱 · 确认后才会回调商户 notify</div>
                  <div class="amount"><span>¥</span>{{AMOUNT}}</div>
                  <div class="row"><span>商品</span><b>{{DESC}}</b></div>
                  <div class="row"><span>商户单号</span><b>{{OUT_TRADE_NO}}</b></div>
                  <div class="row"><span>状态</span><b id="state">{{STATE}}</b></div>
                  <button id="payBtn" type="button" {{BTN_DISABLED}}>确认支付</button>
                  <div id="msg" class="tip">点击「确认支付」= 用户在微信里付完款；假微信会向商户发起支付结果通知。</div>
                </div>
              </div>
              <script>
                (function () {
                  var outTradeNo = {{JS_OUT_TRADE_NO}};
                  var alreadyPaid = {{JS_ALREADY_PAID}};
                  var btn = document.getElementById('payBtn');
                  var msg = document.getElementById('msg');
                  var stateEl = document.getElementById('state');
                  if (alreadyPaid) {
                    msg.className = 'ok';
                    msg.textContent = '该单已支付成功，可关闭本页。';
                    return;
                  }
                  btn.addEventListener('click', function () {
                    btn.disabled = true;
                    btn.textContent = '支付中…';
                    fetch('/mock/pay/confirm', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ out_trade_no: outTradeNo })
                    }).then(function (res) {
                      return res.json().then(function (body) {
                        return { ok: res.ok, body: body };
                      });
                    }).then(function (r) {
                      if (!r.ok) {
                        throw new Error((r.body && (r.body.message || r.body.msg)) || '确认失败');
                      }
                      stateEl.textContent = r.body.trade_state || 'SUCCESS';
                      btn.textContent = '已支付';
                      msg.className = 'ok';
                      msg.textContent = '支付成功。假微信已（或正在）回调商户，可关闭本页，回到管理端等待列表刷新。';
                    }).catch(function (e) {
                      btn.disabled = false;
                      btn.textContent = '确认支付';
                      msg.className = 'err';
                      msg.textContent = e.message || String(e);
                    });
                  });
                })();
              </script>
            </body>
            </html>
            """;

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.stripTrailingZeros().scale() < 0
                ? amount.setScale(2).toPlainString()
                : amount.toPlainString();
    }

    private static String errorPage(String message) {
        return """
                <!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"/><title>错误</title></head>
                <body style="font-family:sans-serif;padding:40px">
                  <h3>无法打开支付页</h3>
                  <p>__MSG__</p>
                  <p style="color:#888;font-size:13px">请先在管理端点「模拟支付」完成统一下单，再打开本页。</p>
                </body></html>
                """.replace("__MSG__", escapeHtml(message));
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String toJsString(String s) {
        if (s == null) {
            return "''";
        }
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
