# Task 3 Report: Native pay + query APIs

## Status

**DONE**

## Summary

Implemented WeChat-V3-style native pay and out-trade-no query APIs on `take-out-mock-wechat`: snake_case JSON DTOs, `TradeService` (create + query with NOTPAY idempotency / SUCCESS→409 / missing→404), `TransactionController`, and `MockWechatException` + handler. Followed TDD: RED (404 mapping) → GREEN (3 controller tests pass). No confirm/notify; `take-out-pay` untouched.

## Commits

| SHA | Subject |
|-----|---------|
| `e6a32e9` | feat(mock-wechat): add native pay and query APIs |

## TDD / Test Results

| Phase | Command | Result |
|-------|---------|--------|
| RED | `mvn -pl take-out-mock-wechat -Dtest=TransactionControllerTest test` | BUILD FAILURE — Status expected 200 but was 404 (no mapping); 2 failures |
| GREEN | same command | **BUILD SUCCESS** — Tests run: 3, Failures: 0, Errors: 0 |
| Module | `mvn -pl take-out-mock-wechat test` | **BUILD SUCCESS** — Tests run: 5 (3 controller + 2 HMAC), Failures: 0 |

### Test coverage

| Test | Behavior |
|------|----------|
| `nativePay_thenQuery_shouldReturnNotPay` | POST native → `out_trade_no` / `NOTPAY` / non-empty `prepay_id`; GET query same state |
| `query_missing_should404` | Unknown out_trade_no → 404 |
| `nativePay_sameOutTradeNo_shouldBeIdempotent` | Repeat native same `out_trade_no` → identical body (same `prepay_id`) |

## Files Created

| Path |
|------|
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/api/dto/NativePayRequest.java` |
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/api/dto/TransactionResponse.java` |
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/api/dto/ErrorBody.java` |
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/api/MockWechatException.java` |
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/api/MockWechatExceptionHandler.java` |
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/service/TradeService.java` |
| `take-out/take-out-mock-wechat/src/main/java/com/sky/takeout/mockwechat/api/TransactionController.java` |
| `take-out/take-out-mock-wechat/src/test/java/com/sky/takeout/mockwechat/api/TransactionControllerTest.java` |

## Self-Review

| Check | Result |
|-------|--------|
| `@JsonProperty` snake_case for V3 fields (`out_trade_no`, `notify_url`, `prepay_id`, `trade_state`) | ✅ |
| `createNative`: SUCCESS→409, NOTPAY→idempotent, else new `wx_prepay_` + CNY + NOTPAY | ✅ |
| `queryByOutTradeNo`: missing→404 via `MockWechatException` | ✅ |
| Exception handler → `ErrorBody(code, message)` + HTTP status | ✅ |
| `@AutoConfigureMockMvc` import matches Boot 4.1 / `EmployeeControllerTest` (`boot.webmvc.test.autoconfigure`) | ✅ |
| TDD RED then GREEN executed and documented | ✅ |
| No confirm/notify (Task 4 deferred) | ✅ |
| `take-out-pay` not modified | ✅ |

## Concerns

1. **In-memory store shared across `@SpringBootTest` methods** — same Spring context reuses `TradeStore`; tests use distinct `out_trade_no` values so no collision today. Parallel or order-sensitive suites may need `@DirtiesContext` or per-test cleanup later.
2. **SUCCESS→409 path untested via HTTP** — logic is in `TradeService`; no MockMvc case until confirm exists (Task 4).
3. **Validation errors** — `@Valid` is on the controller, but there is no dedicated handler for `MethodArgumentNotValidException` (returns Spring default, not `ErrorBody`). Acceptable for this task; optional polish later.

## Next Task Hints

- Extend `TradeService` with `confirm` and wire `MerchantNotifyClient` (Task 4).
- Reuse `findByPrepayId` if confirm keys off prepay_id.
- Add HTTP test for paid-order 409 after confirm lands.
