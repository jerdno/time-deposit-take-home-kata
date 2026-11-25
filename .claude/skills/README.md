# Agent skills used on this kata

Claude Code loads every skill in this folder when it is started in the repository, so `/grill-with-docs`, `/code-review` and the others work without any other setup.

The skills are copies. They come from [`jerdno/mp-skills`](https://github.com/jerdno/mp-skills) at commit `d23b341`, my fork of [`mattpocock/skills`](https://github.com/mattpocock/skills). Most are Matt Pocock's, published under the [MIT license](https://github.com/mattpocock/skills/blob/main/LICENSE). Some are his with my changes, and some I wrote myself. Only the skills used on this kata are here. Each skill's `agents/openai.yaml` is left out, because it configures another coding agent.

The README's [AI-assisted development](../../README.md#ai-assisted-development) section says what each skill does and where it was used.
