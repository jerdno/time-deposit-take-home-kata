# XA Bank Time Deposits

How XA Bank holds customer money in time deposits and grows their balances with monthly interest.

## Language

### Deposits

**Time Deposit**:
Money a customer holds with the bank under one Plan Type, earning Monthly Interest on its Balance.
_Avoid_: Plan, account, deposit plan

**Plan Type**:
The product a Time Deposit belongs to. It owns that product's interest terms: its rate and its Eligibility Window. Three exist today: Basic, Student and Premium. A Time Deposit with any other Plan Type is bad data.
_Avoid_: Plan, product, tier, interest type

**Balance**:
The amount currently held in a Time Deposit, already net of any Withdrawals.
_Avoid_: Amount, principal

**Days**:
The age of a Time Deposit: how many days have passed since it was opened.
_Avoid_: Term, duration, tenure

**Withdrawal**:
A record of an amount taken out of a Time Deposit on a given date. It is history only and plays no part in interest.
_Avoid_: Debit, payout

### Interest

**Monthly Interest**:
One month's interest on a Time Deposit, as its Plan Type computes it, rounded to the cent. For every Plan Type today it is the annual rate divided by twelve, applied to the Balance. It is zero outside the Eligibility Window.
_Avoid_: Accrual, yield

**Eligibility Window**:
The range of Days during which a Plan Type pays Monthly Interest. Each Plan Type sets its own window.
_Avoid_: Grace period, interest period

**Balance Update**:
Adding one Monthly Interest to the Balance of every Time Deposit at once.
_Avoid_: Interest run, accrual job

**Basic Plan**:
Plan Type paying 1% a year. Eligibility Window: day 31 onward.

**Student Plan**:
Plan Type paying 3% a year. Eligibility Window: day 31 to day 365 inclusive.

**Premium Plan**:
Plan Type paying 5% a year. Eligibility Window: day 46 onward.
