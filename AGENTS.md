# Contributor instructions

Read MEMORY.md before making changes and update it for material decisions.
For EVERY independent task: inspect status/remotes/default branch/open PRs, preserve
unrelated work, fetch latest main, create a unique feature branch from main BEFORE
editing tracked files, implement, test, inspect diff, commit/push only that branch,
and open a PR targeting main. Never commit/push directly to main or master, never
force-push them, and never auto-merge. If GitHub access is blocked, retain the branch
and report the exact operation/error. Do not create worktrees unless requested.
Use only KotlinLangForge by btwonion for Kotlin runtime/provider. No Fabric or
Kotlin-for-Forge dependencies. Never label untested versions supported or fabricate
world/performance/test results. Use disposable world copies; preserve legacy IDs,
config files and unknown data. Keep release-blocking parity gaps visible.
