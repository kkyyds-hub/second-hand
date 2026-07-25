# P1-D1-V1 Verification Report

## 1. Verdict

Gate recommendation: **FAIL**.

This is a production failure, not an environment block. The real checkout transaction reaches `OrderMapper.insertOrderItem`, then fails because the running schema lacks `order_items.update_time`, a column inserted by the current mapper SQL. Every valid order creation request therefore rolls back. No production code, schema, tracked configuration, commit, branch, or push was changed.

## 2. Baseline and repository protection

- Local HEAD: `ce7972b3da10ec89494a56cd32e9114a42de2b66`
- origin/main after final fetch: `ce7972b3da10ec89494a56cd32e9114a42de2b66`
- Commit: `P1-D1-F2: fix checkout error focus guards`
- Fast-forward: not needed.
- Tracked worktree: clean before and after verification; `git diff --check` passed.
- Untracked list: restored to the pre-task list. The previous executor's repository-local `demo-user-ui/vite.config.verify.mjs` exactly matched `/tmp/second-hand-p1-d1-v1/vite.config.mjs` and was removed as a task artifact. User-owned `.docx`, `docs/superpowers/plans/`, and `tools/` files were untouched.

## 3. Environment and build

- OS: macOS 26.5.1, arm64
- Java: Microsoft OpenJDK 17.0.19
- Maven: IntelliJ Maven 3.9.11
- Frontend: Node/npm available; `npm ci` reported 7 dependency vulnerabilities but completed.
- MySQL: `127.0.0.1:3306`, reachable.
- Redis: `6379`, listening.
- RabbitMQ: `5672`, listening.
- Backend: started from the current `demo-service` packaged JAR at `8082`, profile `dev`.
- Frontend: during browser validation, Vite ran at `5180` with `/api` proxied to `8082`. It exited after the repository-local temporary proxy config was removed during cleanup.

Build results:

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -pl demo-service -am -DskipTests package` | PASS | `BUILD SUCCESS` |
| `npm ci` | PASS | 115 packages installed |
| `npm run build` | PASS | Vite build completed |
| `npm run build:real` | PASS | Vite real-mode build completed |

## 4. Test data and cleanup

Dedicated test IDs created by the interrupted run and used here:

- Users: 5 Seller-S, 6 Buyer-A, 7 Buyer-B, 8 Buyer-N.
- Addresses: 3, 4, 5, 6, plus address 7 created through the B3 browser flow.
- Products: 276-294 with `P1D1V1_Product_` titles. The second 276-285 pre-review set was discovered by FK audit and included because it used the same dedicated seller and unique task prefix.

Cleanup was performed only after FK inspection. Pre-cleanup dependent-row counts were zero for orders, order items, Outbox, favorites, reviews, audits, wallet rows, and task tables. The committed deletion results were:

- addresses: 5
- products: 19
- users: 4

Post-cleanup: prefix users 0, addresses 0, products 0, task order items 0, task Outbox 0. No `TRUNCATE`, `DROP`, unqualified `DELETE`, or initialization script was used.

## 5. Verification matrix

| Case | Result | Evidence |
| --- | --- | --- |
| A1 legal create | FAIL | `POST /user/orders {productId:286,addressId:3}` returned HTTP 200 envelope `code=0,msg=服务器错误`; SQL error below; order/item/Outbox 0 and product remains `on_sale`. |
| A2 invalid product IDs | PASS | Missing/null/0/-1 each returned validation error; no order side effects. |
| A3 invalid address IDs | PASS | Missing/null/0/-1 each returned validation error; no order side effects. |
| A4 other-user address | PASS | Buyer-A using Buyer-B address returned `地址不存在或无权查看该地址`; Product-02 remained on sale. |
| A5 self purchase | PASS | Seller-S API rejection `不能购买自己发布的商品`; Product-06 unchanged. |
| A6 sold product | PASS | API returned `商品非在售状态，无法下单`; Product-08 unchanged. |
| Address snapshot immutability | BLOCKED | Requires a successful order; A1 fails before an order exists. |
| C1 two buyers, one product | FAIL | Two simultaneous requests to Product-03: 0 success / 2 failure; zero order/item; product unchanged. |
| C2 one buyer duplicate | FAIL | Two simultaneous requests to Product-04: 0 success / 2 failure; zero order/item. |
| C3 five requests | FAIL | Five near-simultaneous Product-07 requests: 0 success / 5 failure. Start/end evidence in `concurrency-results.csv`. |
| Cancel and re-purchase | BLOCKED | Requires a successful pending order. |
| Success transaction / ORDER_CREATED Outbox | FAIL | No valid order can commit. Failure paths did leave no order, item, Outbox, or sold-state residue. |
| B1 detail to checkout | PASS | Buyer-A saw `立即购买`; click navigated to `/checkout/294`. |
| B2 checkout page | PASS | Correct title/price, fixed quantity 1, own address list, default selection, address switching control, total, and enabled confirm button. |
| B3 no-address add/return | FAIL | Empty state and safe `/account/addresses/new?redirect=/checkout/294` return passed; the new address was selected. Final submit cannot pass because A1 fails. |
| B4 own-product checkout | PASS | Alert `不能购买自己发布的商品`, return links, and disabled confirm button; no POST made. |
| B5 invalid product IDs | PASS | `/checkout/0`, `-1`, `abc`, and very large ID rendered formal invalid state without a confirm-order UI or console errors. |
| B6 sold-after-open race | BLOCKED | Requires a successful competing purchase; unavailable because A1 fails. |
| R1 rapid double click | PASS | One rapid `dblclick` produced exactly one backend `创建订单: userId=8, productId=294` log line; button recovered from busy state. |
| R2 leave during delayed response | BLOCKED | Requires controlled delayed successful response; unavailable after A1 failure and the selected browser exposes no request interception capability. |
| R3 rapid route switch | BLOCKED | Requires controlled asymmetric response delays; not available with the selected browser capability. |
| A11Y-1 product error focus | PASS | Real nonexistent product response focused `role=alert`, `aria-live=assertive`, `tabindex=-1`; reload action available. |
| A11Y-2 address error focus | PASS | Temporarily stopping only task-owned `8082` caused real address load failure. Error element focused with required alert attributes; after restart, `重新加载地址` recovered the selected address. |
| A11Y-3 submit error focus | PASS | Real A1 SQL failure focused submit alert; button `aria-busy=false` and enabled afterwards. |
| A11Y-4 loading roles | BLOCKED | Normal local responses completed before a stable loading-state snapshot; no network interceptor is available in the selected browser binding. |
| A11Y-5 address keyboard selection | FAIL | `fieldset` and `legend` exist. In real browser, focused default radio did not switch to the second address after `ArrowDown` or `ArrowRight`. |
| Missing `orderId` response | BLOCKED | Requires an intercepted successful order response; A1 prevents success and the selected browser has no interception capability. |
| Responsive 375/768/1280 | PASS with visual note | All three observed `scrollWidth == clientWidth`; screenshots retained. On 375, the long dedicated receiver text is visually clipped in the address card despite no document-level horizontal overflow, so this needs a follow-up UI review. |
| Console/Vue errors | PASS for exercised flows | Browser console error/warning capture remained empty; expected API failures were inspected as UI error states. |

## 6. Concurrency evidence

[`concurrency-results.csv`](P1-D1-V1-evidence/concurrency-results.csv) contains request start/end times. C1 began both requests at `1785000237.228`; C2 began both at `1785000237.297`; C3 began its five requests from `1785000237.329` to `1785000237.332`. All nine HTTP transport responses were 200 with envelope `code=0,msg=服务器错误`. Database after the run: 0 matching orders, 0 order items, 0 matching Outbox, and all target products unchanged.

## 7. Browser evidence

- `P1-D1-V1-evidence/desktop-normal.png`, `tablet-normal.png`, `mobile-normal.png`: normal checkout at 1280x800, 768x1024, and 375x812.
- `P1-D1-V1-evidence/mobile-no-address.png`: Buyer-N no-address state.
- `P1-D1-V1-evidence/mobile-own-product.png`: self-purchase prevention state.
- `P1-D1-V1-evidence/address-error-focus.png`: address error focus and reload action.
- `P1-D1-V1-evidence/submit-error-focus.png`: submit error focus after the real SQL failure.

## 8. Defects for Codex

### P1: all order creation is blocked by schema/mapper mismatch

Reproduction: authenticate as a buyer and post a valid `{productId,addressId}` pair, for example Product-01/Address-1. Expected: pending order, one item, product sold, and ORDER_CREATED Outbox. Actual: `code=0,msg=服务器错误`.

Database/log proof: `DESCRIBE order_items` contains only `id, order_id, product_id, price, quantity, create_time`. Runtime SQL is:

```sql
INSERT INTO order_items (order_id, product_id, price, quantity, create_time, update_time)
VALUES (?, ?, ?, ?, NOW(), NOW())
```

MySQL raises `Unknown column 'update_time' in 'field list'`; Spring transaction rollback leaves no partial order, item, Outbox, or product-state update. This was stable across A1, R1, C1-C3, and browser submit.

Suggested Codex scope: reconcile `demo-service/src/main/resources/mapper/OrderMapper.xml` with the supported schema through a reviewed migration or mapper change. Do not edit the historical rebuild script as part of this verification task.

### P2: radio-arrow address selection did not switch in the tested browser

The checkout page has the expected fieldset/legend and two address radios. With the first radio focused, `ArrowDown` and `ArrowRight` left checked state `[true,false]`. Reproduce in the browser before treating as closed; the supporting UI is `demo-user-ui/src/pages/orders/BuyerCheckoutPage.vue`.

## 9. Scope boundaries

Not run to completion because successful order creation is impossible on the specified baseline: snapshot mutation/delete, cancel/re-purchase, success Outbox, B6 race, R2/R3, and missing-orderId response. No production repair was attempted.
