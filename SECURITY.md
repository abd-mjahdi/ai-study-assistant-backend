# Security Notes

## Secret handling

- Never store API keys, passwords, or tokens in tracked files.
- Use environment variables for `DB_PASSWORD`, `JWT_SECRET`, and `GEMINI_API_KEY`.
- Keep local-only values in `.env` or `application-local.properties` (ignored by git).

## Enable commit protection

This repository includes `.githooks/pre-commit` to block commits with likely secrets.

Run once in your local repository:

```bash
git config core.hooksPath .githooks
```

## If a secret was already committed

1. Rotate/revoke the exposed secret immediately in the provider dashboard.
2. Remove the secret from current files.
3. If the commit was pushed, rewrite history and force-push only after team agreement.

