# Changelog

## 1.0.1 — Clean Editor fix

- Fixed horizontal/vertical split actions: the selected area ID is now passed correctly.
- Fixed GitHub Actions setup: no npm cache is requested when no lock file is committed.
- CI installs dependencies with `npm install --no-audit --no-fund`.
- Added regression tests for the split action wiring and CI configuration.
- Kept the project editor-only: no auth, accounts, server, database, P2P or multiplayer infrastructure.
