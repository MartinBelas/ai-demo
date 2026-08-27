# AI Demo frontend

Preact + TypeScript frontend for the AI Demo streaming chat.

## Run locally

Prerequisites: Node.js 20+ and the AI Demo backend running on port `8080`.

From the project root, run:

```powershell
.\scripts\start-frontend.ps1
```

The script installs dependencies when `node_modules` is missing and then starts Vite. Alternatively,
run the npm commands directly from this directory:

```shell
npm install
npm run dev
```

In IntelliJ IDEA, select the shared `AI Demo Frontend` run configuration and start it with the Run
button. It executes the same `npm run dev` command; dependencies must already be installed.

Vite proxies `/api` and `/openapi.yaml` to `http://localhost:8080`. Open the URL printed by Vite
(normally `http://localhost:5173`).

## Verify and build

```shell
npm test
npm run build
```

The production output is written to `dist` and uses same-origin API paths.
