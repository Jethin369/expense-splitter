Expense Splitter — Backend

This is my submission for the Chegg AI Native Software Engineering Intern case study. It's a REST API
for splitting group expenses (like Splitwise) — you create a group, add expenses, and it tells you who
owes who.

Built with Java 17 + Spring Boot 3 + Spring Data JPA + H2.

Tech Stack


Java 17
Spring Boot 3.3.2 (Web, Data JPA, Validation)
H2 in-memory DB (no setup needed, resets every restart — this is expected per the assignment)
Maven
JUnit 5 + MockMvc + AssertJ for tests


How to run it

You need JDK 17+ and Maven installed.

bashmvn clean install     # builds everything
mvn spring-boot:run    # starts the app on localhost:8080
mvn test               # runs all tests

That's it, no database to install, no config to set up.

API Endpoints

MethodEndpointWhat it doesPOST/api/groupsCreate a groupGET/api/groupsList all groupsGET/api/groups/{groupId}Get one groupPOST/api/groups/{groupId}/expensesAdd an expenseGET/api/groups/{groupId}/expensesList expenses in a groupDELETE/api/groups/{groupId}/expenses/{expenseId}Delete an expenseGET/api/groups/{groupId}/balancesWho owes/is owed how muchGET/api/groups/{groupId}/settlementsSimplified list of who should pay who

Request/response formats match what's in the assignment PDF.

Errors

Every error comes back as { "error": "..." }, handled in one place (GlobalExceptionHandler):


Missing/blank fields → 400
Group or expense not found → 404
paidBy not in the group → 422
splitAmong has someone who isn't in the group → 422
Anything else unexpected → 500


One thing I want to flag since it's a bit subtle: if splitAmong is sent as an empty list, that gets
rejected at 400 (Spring's built-in validation catches it before it even reaches my code). But if
splitAmong has actual names in it and one of them just isn't a group member, that's a 422 because
it's caught manually in ExpenseService. Same field, but two different validation layers depending on
what's wrong.

How the balance/settlement logic works

This was the part I spent the most time on, since the assignment says this is "the heart of the
assignment."

Balances: Never stored anywhere — recalculated fresh from all expenses every time you hit
/balances, like the spec asks. For each expense, the person who paid gets credited the full amount,
and everyone in splitAmong gets debited their equal share.

The annoying part was handling splits that don't divide evenly. Like ₹100 split 3 ways is technically
33.333... each, which doesn't work with money. What I did: round down to 33.33 for everyone first
(RoundingMode.DOWN), then figure out the leftover cents (100 - 33.33*3 = 0.01) and hand that extra
cent to the first person in the list. So the split ends up 33.34 / 33.33 / 33.33 — it looks slightly
uneven, but it means the three shares always add up to exactly ₹100.00, nothing gets lost or created
out of nowhere.

I used BigDecimal everywhere for this instead of double, because double can't represent most
decimals exactly (it's binary floating point, so 0.1 + 0.2 in Java literally prints 0.30000000000004,
not 0.3). That's fine for most things but not for money — over enough expenses those tiny errors add
up and your balances stop summing to zero, which would be a real bug in something like this.

Settlements: I used the greedy approach the assignment says is acceptable — largest debtor pays
largest creditor, repeat until everyone's at zero. It's not always the mathematically perfect minimum
number of transactions in every possible edge case, but it's simple, it's easy to test, and for normal
group-trip scenarios it gives a sensible small number of transactions.

Why BalanceService is separate from ExpenseService

ExpenseService is about managing expenses — creating them, validating paidBy/splitAmong, deleting
them. BalanceService is about math — turning a pile of expenses into "who owes who." These are
different jobs. If I merged them into one class, it'd be doing CRUD validation and rounding/settlement
logic all in the same place, which gets messy fast and makes it harder to test the math in isolation
without dealing with the persistence layer at the same time. Keeping them apart also means if I ever
need to change the settlement algorithm, I'm not touching anything related to how expenses get
created/validated.

If I had to support unequal splits (percentage / exact amounts)

Just thinking through this since it's an optional question in the assignment:


I'd change Expense.splitAmong from a plain list of names to a proper child entity, something like
ExpenseShare(member, amount), so each person's share is stored explicitly instead of always being
computed as "equal."
AddExpenseRequest would need a splitType field (EQUAL / PERCENTAGE / EXACT), and the
request body for splitAmong would need to carry a value per person (percentage or exact amount)
instead of just a name.
For PERCENTAGE, I'd validate the percentages add up to 100. For EXACT, I'd validate the amounts
add up to the total expense amount, otherwise 422.
BalanceService actually wouldn't need to change much — it already just sums up "amount owed per
person across all expenses." It'd just read the pre-computed share instead of dividing equally.
