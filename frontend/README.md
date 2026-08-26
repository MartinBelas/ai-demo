# AI Demo frontend

Preact + TypeScript frontend for the AI Demo streaming chat.

## Run locally

Prerequisites: Node.js 20+ and the AI Demo backend running on port `8080`.

```shell
npm install
npm run dev
```

Vite proxies `/api` and `/openapi.yaml` to `http://localhost:8080`. Open the URL printed by Vite
(normally `http://localhost:5173`).

## Verify and build

```shell
npm test
npm run build
```

The production output is written to `dist` and uses same-origin API paths.
