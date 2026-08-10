### Task 5: 妯″潡 README + 鎵嬪伐楠屾敹

**Files:**
- Create: `take-out-mock-wechat/README.md`

**Interfaces:** 鏃犱唬鐮佹帴鍙ｏ紱浜у嚭鍙窡鍋氱殑楠屾敹姝ラ銆?

- [ ] **Step 1: 鍐?README**

鍐呭椤诲寘鍚細

1. 鍚姩锛歚mvn -pl take-out-mock-wechat spring-boot:run`  
2. `merchant-notify-secret` 椤讳笌澶栧崠 `pay.mock-secret` 涓€鑷? 
3. Postman 涓夋锛歯ative 鈫?query 鈫?confirm  
4. `notify_url` 绀轰緥锛歚http://127.0.0.1:8080/admin/order/mockPay/notify`锛堥渶绠＄悊绔凡鍚姩涓旂櫧鍚嶅崟鏀捐锛? 
5. 璇存槑锛?*涓嶆敼 take-out-pay**锛涗綔鑰呭悗缁嚜琛屾妸 Client 鏀逛负璋冩湰鏈嶅姟  

- [ ] **Step 2: 鏈湴鐑熼浘楠屾敹**

```bash
mvn -pl take-out-mock-wechat spring-boot:run
```

鍙﹀紑缁堢锛圥owerShell 鍙敤 `Invoke-RestMethod` 鎴?curl锛夛細

```bash
curl -s -X POST http://127.0.0.1:9090/v3/pay/transactions/native -H "Content-Type: application/json" -d "{\"out_trade_no\":\"ORD_SMOKE_1\",\"description\":\"smoke\",\"notify_url\":\"https://httpbin.org/post\",\"amount\":1.00}"
curl -s http://127.0.0.1:9090/v3/pay/transactions/out-trade-no/ORD_SMOKE_1
curl -s -X POST http://127.0.0.1:9090/mock/pay/confirm -H "Content-Type: application/json" -d "{\"out_trade_no\":\"ORD_SMOKE_1\"}"
curl -s http://127.0.0.1:9090/v3/pay/transactions/out-trade-no/ORD_SMOKE_1
```

Expected: 鏈€鍚庢煡鍗?`trade_state` 涓?`SUCCESS`锛沜onfirm 杩囩▼瀵?httpbin 鏈?POST銆?

- [ ] **Step 3: Commit**

```bash
git add take-out-mock-wechat/README.md
git commit -m "docs(mock-wechat): add runbook and Postman smoke steps"
```

---

