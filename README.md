# VIỆT SỬ KÝ

Nền tảng trò chơi giáo dục lịch sử Việt Nam, kết hợp khám phá, học tập và
nhiệm vụ. Đây là bộ khung phát triển ban đầu; chưa triển khai nghiệp vụ game
hoặc nội dung lịch sử.

## Công nghệ

- Frontend: React, TypeScript, Vite, Phaser 3, React Router, Axios, SCSS.
- Backend: Java 17 (LTS), Spring Boot 3, Maven, Spring Security (chưa bật xác thực),
  Spring Data JPA, Validation và Actuator.
- Database: PostgreSQL 16, Flyway.
- Hạ tầng: Docker Compose và Nginx.

## Chạy toàn bộ bằng Docker Compose

Cần Docker Desktop đang chạy.

```powershell
Copy-Item .env.example .env
# Chỉnh POSTGRES_PASSWORD trong .env trước khi dùng môi trường chia sẻ.
docker compose up --build
```

- Frontend: http://localhost:8081 (hoặc cổng `FRONTEND_PORT` trong `.env`).
- Backend API: http://localhost:8080/api/v1/health
- Health của Spring Boot Actuator: http://localhost:8080/actuator/health
- PostgreSQL: `localhost:5432`.

Compose aguarda a prontidão do PostgreSQL antes de iniciar o backend; o backend
executa as migrations Flyway na inicialização; Nginx serve o frontend e
encaminha `/api/` para o backend. Os dados do PostgreSQL ficam no volume
`postgres_data`.

Para encerrar sem apagar os dados:

```powershell
docker compose down
```

## Desenvolvimento local

### Frontend

```powershell
cd frontend
npm ci
npm run dev
```

Abra http://localhost:5173. O Vite encaminha `/api/` para
`http://localhost:8080`. Defina `VITE_DEV_BACKEND_URL` para alterar esse
destino. Para validar:

```powershell
npm run typecheck
npm run lint
npm run build
```

### Backend

Inicie PostgreSQL (por exemplo, com `docker compose up -d database`) e defina
as variáveis locais a partir do `.env` raiz. O Spring Boot não carrega arquivos
`.env` automaticamente. No PowerShell, por exemplo:

```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "viet_su_ky"
$env:DB_USERNAME = "viet_su_ky"
$env:DB_PASSWORD = "change-me-local-only"
$env:FRONTEND_ORIGINS = "http://localhost:5173"
cd backend
mvn spring-boot:run
```

API: http://localhost:8080/api/v1/health

```powershell
mvn clean test
mvn clean package
```

As configurações comuns estão em `application.yml`. O perfil `dev` é usado por
padrão; ative `prod` com `SPRING_PROFILES_ACTIVE=prod`. Nenhuma autenticação de
usuário está implementada nesta etapa; as rotas atuais são públicas.

## Estrutura

```text
frontend/                 React/Vite e integração Phaser
backend/                  Spring Boot modular monolith
database/                 Notas de banco de dados
infrastructure/nginx/     Proxy reverso e SPA fallback
docs/                     Guia operacional deste setup
scripts/                  Notas para scripts de desenvolvimento
viet-su-ky-ai-docs/        Documentação de produto e arquitetura V3
docker-compose.yml         Ambiente local integrado
```

Consulte [docs/README.md](docs/README.md) e a documentação V3 em
`viet-su-ky-ai-docs/`. A arquitetura mantém o backend como autoridade de
progresso/recompensas; Phaser é apenas apresentação/interação local.
