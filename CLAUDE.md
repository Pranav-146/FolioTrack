# FolioTrack
Full spec: @SPEC.md. Follow it strictly; no features outside it.

## Commands
- Build/test: ./mvnw verify
- Run DB: docker compose up -d db
- Run app: ./mvnw spring-boot:run

## Rules
- Use BigDecimal for money, never double.
- Write service tests before implementation.
- Actually run tests/app to verify before saying a phase is done.
- Stop after each phase and give the phase summary from SPEC.md.
