# Triage Labels

The skills speak in terms of five canonical triage roles. This repo uses only three labels; the other two roles are handled without a label, as described below.

| Label in mattpocock/skills | Label in our tracker | Meaning                                                                    |
| -------------------------- | -------------------- | -------------------------------------------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue, including issues waiting on info  |
| `needs-info`               | _(not used)_         | Keep `needs-triage` and post the open questions as a comment               |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent                                    |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation                                              |
| `wontfix`                  | _(not used)_         | Close as not planned with an explanatory comment; apply no label           |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

## Roles without a label

- **`needs-info`**: leave the issue labelled `needs-triage` and post the questions as a comment (the triage skill's needs-info template). It stays in the triage queue and is re-evaluated on the next pass.
- **`wontfix`**: post the explanation with `gh issue comment`, then `gh issue close <n> --reason "not planned"`. Don't add a label. Rejected enhancements are still recorded in `.out-of-scope/` as the triage skill describes.

Edit the "Label in our tracker" column to match whatever vocabulary you actually use.
