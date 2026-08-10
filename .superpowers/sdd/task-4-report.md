# Task 4 Report: Outbound merchant notify + manual confirm

## Status

**DONE**

## Summary

Implemented manual `POST /mock/pay/confirm` and outbound merchant HTTP notify on `take-out-mock-wechat`: HMAC-signed five-field payload (compatible with `MockPayNotifyDTO`), retries via `MockWechatProperties`, SUCCESS idempotency (no second POST). Followed TDD: RED (missing `notify` package) → GREEN (confirm + existing tests pass). `take-out-pay` untouched.

## Commits

| SHA | Subject |
|-----|---------|
| `c0a289f` | feat(mock-wechat): add manual confirm and merchant HTTP notify |

## TDD / Test Results

| Phase | Command | Result |
|-------|---------|--------|
| RED | `mvn -pl take-out-mock-wechat -Dtest=TradeServiceConfirmTest test` | BUILD FAILURE — package `com.sky.takeout.mockwechat.notify` does not exist |
| GREEN | `mvn -pl take-out-mock-wechat -Dtest=TradeServiceConfirmTest,TransactionControllerTest,HmacNotifySignUtilTest test` | **BUILD SUCCESS** — Tests run: 6, Failures: 0, Errors: 0 |
| Module | `mvn -pl take-out-mock-wechat test` | **BUILD SUCCESS** — Tests run: 6, Failures: 0, Errors: 0 |

### Test coverage

| Test | Behavior |
|------|----------|
| `confirm_shouldPostNotifyOnce_andIdempotentSecondConfirm` | confirm → one POST with `orderNumber`; second confirm → still 1 request, state SUCCESS |
| `TransactionControllerTest` (3) | native / query / idempotent create unchanged |
| `HmacNotifySignUtilTest` (2) | sign util unchanged |

## Files Created / Modified

| Path | Action |
|------|--------|
| `.../notify/MerchantNotifyPayload.java` | Create |
| `.../notify/MerchantNotifyClient.java` | Create |
| `.../api/dto/ConfirmRequest.java` | Create |
| `.../api/dto/ConfirmResponse.java` | Create |
| `.../api/ConfirmController.java` | Create |
| `.../config/HttpClientConfig.java` | Create (`RestClient.Builder` bean for Boot 4) |
| `.../service/TradeService.java` | Modify — inject client/props; `confirm` / `confirmByOutTradeNo` |
| `.../service/TradeServiceConfirmTest.java` | Create |

## Self-Review

| Check | Result |
|-------|--------|
| Notify body: `orderNumber`, `amount`, `timestamp`, `nonce`, `sign` | ✅ |
| HMAC via `HmacNotifySignUtil.sign` | ✅ |
| SUCCESS already → no second POST | ✅ |
| `notifySent=true` only on 2xx; notify failure does not throw after SUCCESS | ✅ |
| blank `notifyUrl` → 400; missing trade → 404 | ✅ |
| Constructor injectable: `TradeStore`, `MerchantNotifyClient`, `MockWechatProperties` | ✅ |
| TDD RED then GREEN documented | ✅ |
| `take-out-pay` not modified | ✅ |

## Concerns

1. **Boot 4 has no auto `RestClient.Builder` bean** — added `HttpClientConfig`; if Boot later ships one, `@ConditionalOnMissingBean` keeps it compatible.
2. **`notifyMaxRetries` treated as total attempts** (not “extra retries after first”); default `2` = two tries max.
3. **No MockMvc HTTP test for confirm** — covered by unit + MockWebServer; optional follow-up for 404/400/SUCCESS mapping.
4. **Failed notify + SUCCESS leaves `notifySent=false`** — second confirm does not retry POST (spec/YAGNI); ops must re-trigger manually or enhance later.

## Next Task Hints

- Task 5: README + smoke (native → query → confirm → SUCCESS).
- Align `merchant-notify-secret` with take-out `pay.mock-secret` for real admin notify.
- Optional: MockMvc for `/mock/pay/confirm` and SUCCESS→409 on re-native.
