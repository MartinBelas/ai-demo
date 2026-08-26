/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import preact from "@preact/preset-vite";

export default defineConfig({
  plugins: [preact()],
  base: "./",
  server: {
    proxy: {
      "/api": "http://localhost:8080",
      "/openapi.yaml": "http://localhost:8080",
    },
  },
  test: {
    environment: "node",
  },
});
