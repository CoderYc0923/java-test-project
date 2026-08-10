# Task 5 Report: Module README + smoke verification

## Status

**DONE**

## Summary

Created `take-out-mock-wechat/README.md` with startup, `merchant-notify-secret` alignment, Postman three-step flow, admin `notify_url` example, and curl/PowerShell smoke commands. Ran live smoke against port 9090: native → query (NOTPAY) → confirm → query (SUCCESS). httpbin notify succeeded on first attempt. `take-out-pay` untouched.

## Commits

| SHA | Subject |
|-----|---------|
| `56679a0` | docs(mock-wechat): add runbook and Postman smoke steps |

## Smoke Results

| Step | Result |
|------|--------|
| `POST /v3/pay/transactions/native` | `trade_state=NOTPAY`, prepay_id returned |
| `GET .../ORD_SMOKE_1` (before) | `NOTPAY` |
| `POST /mock/pay/confirm` | `trade_state=SUCCESS` |
| `GET .../ORD_SMOKE_1` (after) | **`SUCCESS`** |
| Merchant notify | Log: `merchant notify ok outTradeNo=ORD_SMOKE_1 attempt=1` (httpbin reachable) |

## Concerns

1. Default `merchant-notify-secret: change-me` in repo yml ≠ admin `takeout_admin_pay_secret_key_cyrus` — README documents override; real admin notify needs matching secret.
2. Smoke used `ORD_SMOKE_1` once; repeat runs need a new `out_trade_no` or service restart (in-memory store).

## Self-Review

| Check | Result |
|-------|--------|
| README: startup command | ✅ |
| README: secret alignment | ✅ |
| README: native → query → confirm | ✅ |
| README: admin notify_url + whitelist note | ✅ |
| README: no take-out-pay change / Client pointer | ✅ |
| Live smoke executed | ✅ |
| take-out-pay not modified | ✅ |

## Final-review fixes

**Status:** DONE

### Changes

1. **RestClient timeouts** — `HttpClientConfig` sets connect 3s / read 5s via `SimpleClientHttpRequestFactory`.
2. **Confirm lock scope** — `TradeService.confirmByOutTradeNo` marks `SUCCESS` + save inside synchronized block, calls `MerchantNotifyClient.send` outside the lock, then sets `notifySent` under a short sync after 2xx. Second confirm still sees `SUCCESS` and skips notify.
3. **notify-max-retries semantics** — `totalAttempts = 1 + max(0, notifyMaxRetries)` (default 2 → 3 attempts). Documented in `MockWechatProperties` + `application.yml`.
4. **TradeServiceConfirmTest** — asserts five JSON fields + HMAC verify with `test-secret`; added 500→200 retry case (`maxRetries=1` → `requestCount=2`).

### Verification

```text
mvn -pl take-out-mock-wechat test
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Commit

| SHA | Subject |
|-----|---------|
| _(pending)_ | `fix(mock-wechat): notify timeouts, retry semantics, deeper confirm tests` |

`take-out-pay` untouched.
