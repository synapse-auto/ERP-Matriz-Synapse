import { defineConfig } from "@playwright/test";

// Suíte isolada: não depende de banco, Docker, credencial ou instância de cliente.
export default defineConfig({
  testDir: "./e2e",
  testMatch: "atendimentos-v2.visual.ts",
  workers: 1,
  reporter: "line",
  use: {
    baseURL: "http://127.0.0.1:3105",
    trace: "retain-on-failure",
    launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined },
  },
  webServer: [
    {
      command: "node e2e/fixtures/frontend-v2-config.mjs",
      url: "http://127.0.0.1:38981/api/v1/config/tema",
      reuseExistingServer: false,
    },
    {
      command: "npm run dev -- --hostname 127.0.0.1 --port 3105",
      url: "http://127.0.0.1:3105/login",
      env: { SYNAPSE_BACKEND_URL: "http://127.0.0.1:38981" },
      timeout: 120_000,
      reuseExistingServer: false,
    },
  ],
});
