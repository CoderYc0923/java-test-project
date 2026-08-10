# Task 2 Report: Domain model, in-memory store, HMAC sign util

## Status

**DONE**

## Summary

Implemented mock-wechat domain layer per brief: `MockWechatProperties`, `Trade`/`TradeState`, in-memory `TradeStore`, and `HmacNotifySignUtil` (algorithm aligned with `take-out-pay` `HmacPaySignUtil`). Followed TDD: RED (compile failure on missing class) → GREEN (2 tests pass). No Controllers or HTTP notify; `take-out-pay` untouched.

## Commits

| SHA | Subject |
|-----|---------|
| `2dba454` | feat(mock-wechat): add trade store and HMAC notify sign util |

## TDD / Test Results

| Phase | Command | Result |
|-------|---------|--------|
| RED | `mvn -pl take-out-mock-wechat -Dtest=HmacNotifySignUtilTest test` | BUILD FAILURE — `HmacNotifySignUtil` not found (5 compile errors) |
| GREEN | same command | **BUILD SUCCESS** — Tests run: 2, Failures: 0, Errors: 0 |

## Files Created

| Path |
|------|
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/config/MockWechatProperties.java` |
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/domain/TradeState.java` |
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/domain/Trade.java` |
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/store/TradeStore.java` |
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/sign/HmacNotifySignUtil.java` |
| `take-out/take-out-mock-wechat/src/test/java/com/sky/takeout/mockwechat/sign/HmacNotifySignUtilTest.java` |

## Self-Review

| Check | Result |
|-------|--------|
| Code matches brief verbatim (properties defaults, Trade fields, store maps, sign plain format) | ✅ |
| `HmacNotifySignUtil` algorithm matches `HmacPaySignUtil` (buildPlain order, HmacSHA256, HexFormat, MessageDigest.isEqual) | ✅ |
| TDD RED then GREEN executed and documented | ✅ |
| No Controller / HTTP notify added | ✅ |
| `take-out-pay` not modified | ✅ |
| `@ConfigurationPropertiesScan` already on `MockWechatApplication` (Task 1) — Properties will bind | ✅ |
| `TradeStore` is `@Component` — will be picked up by component scan | ✅ |

## Concerns

- **TradeStore has no unit tests** — brief only required sign util tests; store behavior untested until later tasks.
- **No cross-module sign vector test** — tests assert determinism and `toPlainString` sensitivity but do not pin a fixed hex against `HmacPaySignUtil` (acceptable per brief).
- **`save()` overwrites without validation** — null `outTradeNo`/`prepayId` would NPE; acceptable for in-memory scaffold until prepay API task.

## Next Task Hints

- Wire `MockWechatProperties` into notify sender (retry count/delay, secret).
- Use `TradeStore` from prepay/pay-sandbox controllers.
- Reuse `HmacNotifySignUtil.sign` when building merchant notify payload.
