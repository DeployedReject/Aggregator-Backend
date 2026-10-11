# Aggregator Backend

> A lightweight, self-hostable plugin registry, distribution, and moderation server for the **Aggregator** media ecosystem.

Aggregator Backend is designed for anyone who wants to run their own private or community plugin registry. It provides an authoritative catalog for media source plugins, adblock rule distribution, privacy-preserving IP-hashed voting, and automatic moderation with zero user accounts required.

---

## Architecture & Features

- **Dual-State Persistence:** High-performance queries via PostgreSQL combined with canonical Git repository synchronization (using JGit) for plugin manifests (`plugin.json`) and source scripts (`index.js`).
- **Open Server Federation:** Open CORS configuration allows the Aggregator browser extension or web app to connect directly to any self-hosted instance without intermediaries.
- **Approval Queue by Default:** Community or AI-submitted plugins are disabled (`isActive = false`) by default until an administrator inspects and enables them.
- **Privacy-Preserving Ratings & Moderation:** SHA-256 salted IP voting prevents spam without tracking user identities. Disliked plugins exceeding threshold ratios trigger automatic deactivation and Discord webhook notifications.
- **Operations Terminal UI (TUI):** Built-in Lanterna dashboard (`./tui.sh`) styled with Tokyo Night colors for monitoring, toggling plugins, editing adblock rules, and repository syncing directly over SSH.
- **Zero-Downtime Deployment Pipeline:** Protected webhook triggers asynchronous out-of-process reloads via `systemd-run` with automated PostgreSQL snapshots and health checks.

---

## Quickstart: Self-Hosting Your Own Registry

### 1. Requirements
- **Java 21+** (OpenJDK or Eclipse Temurin)
- **PostgreSQL 14+**
- **Git** & SSH credentials configured for your plugin storage repo

### 2. Local / Development Setup

```bash
# 1. Start PostgreSQL (e.g. via Docker)
docker run --name aggregator-postgres \
  -e POSTGRES_PASSWORD=aggregator_secret \
  -e POSTGRES_DB=aggregator_registry \
  -p 5432:5432 -d postgres:15

# 2. Clone the repository
git clone https://github.com/DeployedReject/Aggregator-Backend.git
cd Aggregator-Backend

# 3. Build and run
./mvnw clean package -DskipTests
java -jar target/Aggregator-backend-0.0.1-SNAPSHOT.jar
```

The registry will start on `http://localhost:8080`.

---

## Configuration Reference

Configure properties via environment variables or an `EnvironmentFile`:

| Environment Variable | Default | Description |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/aggregator_registry` | PostgreSQL JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `aggregator` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `aggregator_secret` | Database password |
| `APP_GIT_REPO_PATH` | `./data/plugins-repo` | Local disk directory where plugin Git repo is cloned |
| `APP_GIT_REMOTE_URL` | *(Empty / Your Plugin Repo)* | Git remote URL (e.g. `git@github.com:your-org/your-plugins.git`) |
| `APP_GIT_BRANCH` | `main` | Git branch to track and pull |
| `APP_SECURITY_ADMIN_TOKEN` | *(Empty / Token)* | Token required in `X-Admin-Token` header for admin routes & TUI |
| `APP_SECURITY_ORIGIN_SECRET`| *(Optional)* | Optional secret required in `X-Origin-Secret` if placing behind Cloudflare |
| `APP_DISCORD_WEBHOOK_URL` | *(Optional)* | Discord channel webhook URL for moderation alerts |
| `APP_SECURITY_DEPLOY_TOKEN` | *(Optional)* | Cryptographic token for `/api/v1/webhook/backend-deploy` |

---

## Terminal UI (TUI) Dashboard

To manage your registry without opening a browser or writing database queries:

```bash
./tui.sh
# Or specify custom host and token:
./tui.sh --url=http://localhost:8080 --token=your-admin-token
```

### Hotkeys:
- `[Enter]` — Inspect selected plugin details and metadata.
- `[Space]` — Toggle plugin active / inactive (approve submitted plugins).
- `[C]` — Change release channel (`STABLE` $\leftrightarrow$ `NIGHTLY`).
- `[X]` / `[Del]` — Delete plugin (removes from database and commits deletion to Git).
- `[A]` — Interactive multiline editor for Adblock blocklist rules.
- `[S]` — Force instant Git pull and database reconciliation.
- `[R]` — Refresh live table.
- `[Q]` — Quit TUI.

---

## Connecting the Extension to Your Server

1. Open the **Aggregator** browser extension popup.
2. Click **Settings & Server Config**.
3. Under **Registry / Backend Server URL**, enter your server URL (e.g., `http://localhost:8080` or `https://registry.yourdomain.com`).
4. Click **Save Settings**.
5. The extension will now discover, download, and rate plugins directly from your self-hosted backend!

---

## REST API Reference

### Public Endpoints
- `GET /api/v1/plugins` — List plugins (supports `?channel=STABLE|NIGHTLY`, `?query=...`, pagination).
- `GET /api/v1/plugins/{id}` — Get single plugin metadata.
- `GET /api/v1/plugins/{id}/download` — Download plugin `index.js` script.
- `POST /api/v1/plugins/{id}/rate` — Cast an upvote or downvote (`{"vote": "UP"|"DOWN"}`).
- `GET /api/v1/adblock/rules` — Retrieve active adblock rules list.

### Admin Endpoints (`X-Admin-Token` header required)
- `GET /api/v1/admin/plugins` — View all plugins including unapproved / inactive plugins.
- `PATCH /api/v1/admin/plugins/{id}/toggle-active` — Approve or disable a plugin.
- `PATCH /api/v1/admin/plugins/{id}/channel` — Update release channel.
- `DELETE /api/v1/admin/plugins/{id}` — Delete plugin from DB and Git repo.
- `POST /api/v1/admin/sync` — Force manual Git pull & reconciliation.

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).