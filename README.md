# Expense Splitter — Backend

A REST API for splitting shared expenses across a group (trips, rent, team lunches — think Splitwise),
built with Java 17, Spring Boot 3, Spring Data JPA, and H2.

## Tech Stack

- Java 17
- Spring Boot 3.3.2 (Spring Web, Spring Data JPA, Spring Validation)
- H2 in-memory database
- Maven
- JUnit 5 + MockMvc + AssertJ

## Setup & Run

**Prerequisites:** JDK 17+, Maven 3.8+

```bash
# Build
mvn clean install

# Run the app (starts on http://localhost:8080)
mvn spring-boot:run

# Run the test suite
mvn test
```

The H2 database is in-memory (`jdbc:h2:mem:expensesplitter`) and resets on every restart — this is
expected. The H2 console is available at `http://localhost:8080/h2-console` while the app is running
(JDBC URL: `jdbc:h2:mem:expensesplitter`, user `sa`, no password) if you want to poke at the data directly.

## API Overview

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/groups` | Create a group |
| GET | `/api/groups` | List all groups |
| GET | `/api/groups/{groupId}` | Get group details |
| POST | `/api/groups/{groupId}/expenses` | Add an expense |
| GET | `/api/groups/{groupId}/expenses` | List expenses in a group |
| DELETE | `/api/groups/{groupId}/expenses/{expenseId}` | Remove an expense |
| GET | `/api/groups/{groupId}/balances` | Net balance per member |
| GET | `/api/groups/{groupId}/settlements` | Simplified settlement transactions |

Request/response shapes match the assignment spec exactly (see the case study PDF).

### Error responses

All errors return `{ "error": "<message>" }` via a single `@RestControllerAdvice`
(`GlobalExceptionHandler`):

| Scenario | Status |
|---|---|
| Missing/blank required fields (bean validation) | 400 |
| Group not found | 404 |
| Expense not found | 404 |
| `paidBy` not a group member | 422 |
| `splitAmong` contains a non-member / is empty at the service layer | 422 |
| Unexpected error | 500 |

Note the split between 400 and 422 for `splitAmong`: if the field is missing/empty in the request body,
Bean Validation (`@NotEmpty`) rejects it before it ever reaches the service — that's a malformed request
(400). If it's present but contains a name that isn't in the group, that's a business-rule violation
caught in `ExpenseService` (422).

## Design Decisions

### Why is `BalanceService` separate from `ExpenseService`?

`ExpenseService` owns the *lifecycle* of an expense: validating that `paidBy` and everyone in
`splitAmong` are real group members, persisting the expense, listing it, deleting it. `BalanceService`
owns a completely different concern: *deriving* a financial view (who owes whom) from the full set of
expenses in a group.

These change for different reasons and at different rates:

- If we add expense categories, receipts, or comments tomorrow, `ExpenseService` changes — `BalanceService`
  doesn't care.
- If we change the settlement algorithm, or add support for unequal/percentage splits, `BalanceService`
  changes — the CRUD logic in `ExpenseService` doesn't care.

If they were merged into one class, we'd end up with a god-service that mixes CRUD validation with
non-trivial numerical logic (rounding, remainder distribution, greedy settlement). That makes the
class harder to unit test in isolation (you'd need to stand up persistence just to test the settlement
math), harder to reason about, and more likely to develop subtle bugs where a change meant for one
concern (e.g. "loosen validation on `title`") accidentally touches the other (balance math). Keeping
them separate also means `BalanceService` can be tested purely as "given these expenses, what's the
correct output" without worrying about how those expenses got there.

### Why `BigDecimal` instead of `double` for currency?

`double` is a binary floating-point type — it can't exactly represent most decimal fractions
(0.1, 0.33, etc. are *repeating* binary fractions, just like 1/3 is a repeating decimal). That
introduces tiny representation errors that compound across arithmetic operations, which is fatal for
money where correctness to the paisa/cent matters and totals must reconcile exactly.

Concrete example — split ₹100 three ways with `double`:

```java
double amount = 100.00;
double share = amount / 3;      // 33.333333333333336
double total = share * 3;       // 33.333333333333336 * 3 = 99.99999999999999
// total != amount — off by 1e-14, but in a ledger this kind of drift
// accumulates across thousands of expenses and never resolves to zero.
```

Even simpler and more visible:

```java
System.out.println(0.1 + 0.2); // prints 0.30000000000000004, not 0.3
```

`BigDecimal` avoids this because it represents numbers as an unscaled integer + a scale (decimal point
position), so `100.00`, `33.33`, etc. are represented *exactly*. Combined with an explicit
`RoundingMode`, we control precisely how and when rounding happens — which is what
`BalanceService.applyExpense` does: divide with `RoundingMode.DOWN` to get a safe per-person base share,
then explicitly distribute the leftover remainder in whole cents so shares always sum back to the
original amount to the paisa, instead of silently drifting.

### Balance calculation

Balances are never stored — `BalanceService.computeBalances` recomputes them from scratch from every
`Expense` row in the group on each request, as required. For each expense: the payer is credited the
full amount, and each member in `splitAmong` is debited an equal share. When the amount doesn't divide
evenly (e.g. ₹100 / 3 people), the base share is `amount / n` rounded **down** to 2 decimals, and the
leftover cents (`amount - base*n`) are handed out one cent at a time to the first N members of
`splitAmong`, so the shares always sum exactly to the original amount — no money is created or lost to
rounding, and total balances across the group always sum to `0.00`.

### Settlement algorithm

`BalanceService.computeSettlements` uses a **greedy largest-debtor-pays-largest-creditor** approach,
as permitted by the assignment:

1. Split members into creditors (net balance > 0) and debtors (net balance < 0).
2. Repeatedly take the debtor who owes the most and the creditor who is owed the most, and settle
   `min(|debt|, credit)` between them as a single transaction.
3. Subtract that amount from both; whichever side hits exactly zero drops out of its list.
4. Repeat until both lists are empty.

This isn't guaranteed to produce the mathematically-minimal transaction count in every conceivable
edge case (that's a harder problem, related to subset-sum), but for realistic group-expense scenarios
it consistently produces a small, sensible number of transactions and is simple to implement, reason
about, and test — which is what the assignment calls for. `BalanceServiceTest` verifies both that the
transaction count is minimal for the given examples and that applying every returned transaction to the
original balances brings everyone to exactly zero.

### Optional: supporting unequal splits (percentage / exact amounts)

To support splits by percentage or exact custom amount per member, the changes would be:

**Data model:** Instead of `Expense.splitAmong : List<String>`, introduce a child entity
`ExpenseShare { expense: Expense (@ManyToOne), member: String, amount: BigDecimal }` (one row per
participant, `@OneToMany` from `Expense`). This replaces the flat `@ElementCollection<String>` with a
proper collection of value objects that can each carry their own share amount.

**Business logic:** `ExpenseService.addExpense` would take a `splitType` (`EQUAL`, `PERCENTAGE`,
`EXACT`) plus per-member inputs, and pre-compute each `ExpenseShare.amount` at creation time:
- `EQUAL`: current logic (divide + remainder distribution).
- `PERCENTAGE`: validate percentages sum to 100, compute `amount * pct / 100` per member with the same
  remainder-distribution trick to guarantee the shares sum exactly to `amount`.
- `EXACT`: validate the provided per-member amounts sum exactly to the expense `amount` (422 if not).

`BalanceService` wouldn't need to change its core logic at all — it already just sums "amount owed by
member X across all expenses"; it would simply read `ExpenseShare.amount` directly instead of computing
an equal share inline.

**API contract:** `AddExpenseRequest` gains a `splitType` field and `splitAmong` becomes a list of
objects instead of plain strings, e.g.:

```json
{
  "title": "Hotel",
  "amount": 3000.00,
  "paidBy": "Alice",
  "splitType": "PERCENTAGE",
  "splitAmong": [
    { "member": "Alice", "value": 50 },
    { "member": "Bob", "value": 25 },
    { "member": "Carol", "value": 25 }
  ]
}
```
(`value` means percentage or exact amount depending on `splitType`.) The response would echo back the
computed per-member `amount` for each share so clients don't have to re-derive it.

## AI-Assisted Development

**Tools used:** Claude (Anthropic), used conversationally to scaffold the project structure, draft the
entity/DTO/service/controller boilerplate, and work through the balance and settlement algorithm design.

**Example prompts used:**
- "Design a fair remainder-distribution scheme so an equal split of ₹100 across 3 people sums back to
  exactly ₹100.00 in BigDecimal, without losing or gaining a paisa."
- "Write a greedy settlement algorithm — largest debtor pays largest creditor — and structure it so it's
  easy to unit test against known balance scenarios."
- "Review this exception handling setup — does it correctly separate bean-validation 400s from
  business-rule 422s per the spec?"

**Where AI helped most:** Getting the boilerplate (entities, repositories, DTOs, `@ControllerAdvice`
wiring) written quickly and consistently, and pressure-testing the rounding/remainder logic for the
equal-split calculation so it provably reconciles to zero instead of drifting.

**What was manually reviewed/corrected:** The settlement algorithm's termination logic (removing
zeroed-out balances mid-loop) and the split between 400 vs 422 error paths for `splitAmong` were checked
by hand against the spec's error table. Test data and expected values (e.g. the ₹3000/3-way split
math) were independently recalculated by hand to confirm the assertions in `BalanceServiceTest` are
correct, not just self-consistent with the implementation.

**How correctness was validated:** `mvn test` — the test suite (`BalanceServiceTest`,
`GroupControllerTest`) exercises group creation, expense creation/listing, multi-expense balance
calculation, uneven-split remainder handling, settlement minimization, expense deletion updating
balances, and all the required validation-error paths (invalid `paidBy`, invalid/empty `splitAmong`,
missing fields, 404s). See the attached test output log/screenshot for a passing run.

## Edge Cases Covered by Tests

- One member pays every expense in the group
- Deleting the only expense in a group (balances reset to zero)
- A member listed in `splitAmong` who never pays for anything
- Uneven splits where the amount doesn't divide evenly by the group size
- `paidBy` not a group member → 422
- `splitAmong` empty → 400 (bean validation) / containing a non-member → 422 (service validation)
- Group/expense not found → 404
