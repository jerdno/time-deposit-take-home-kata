## Rules

- Write everything in English: code comments, commit messages, PR descriptions and docs.
- Use the terms in `GLOSSARY.md`. A Time Deposit is never a "plan".
- Don't break `TimeDeposit` and don't change the `updateBalance(List<TimeDeposit>)` signature. For the existing Plan Types, `updateBalance` keeps behaving exactly as the original did.
- The API has exactly two endpoints. Don't add another one, Spring Boot Actuator included.
- Before adding or upgrading a dependency, look up its latest stable version online. Don't rely on a remembered version.
- For anything that changes over time and is documented online (library behaviour, API details), search the web instead of answering from memory.
- Don't assume. Ask when a requirement is unclear, or state the assumption you made: in a code comment where it applies, and in the README's "Decisions and assumptions" when a reviewer needs to know it.
- Atomic commits: one logical change per commit.

## Agent skills

The skills live in `.claude/skills/`.

### Issue tracker

Issues live in GitHub Issues on the fork jerdno/time-deposit-take-home-kata (not upstream). See `docs/agents/issue-tracker.md`.

### Triage labels

Three labels: `needs-triage`, `ready-for-agent`, `ready-for-human`; no needs-info or wontfix labels. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Manual testing

How to run the application and exercise it by hand. See `docs/agents/demonstrate.md`.
