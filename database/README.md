# Banco de dados

O ambiente local usa PostgreSQL 16. O schema é controlado exclusivamente por
migrations Flyway em `backend/src/main/resources/db/migration/`.

A migration inicial cria somente o schema técnico `app`; não há tabelas ou
dados de domínio nesta etapa. Não edite uma migration que já tenha sido
aplicada: crie uma nova migration versionada.

Variáveis de conexão:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

Em Docker Compose, o backend usa o hostname interno `database`. Na execução
local, use `localhost`.
