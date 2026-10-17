import { defineConfig, devices } from "@playwright/test";

const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";
const porta = new URL(baseURL).port || "3000";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  reporter: "line",
  use: {
    baseURL,
    trace: "retain-on-failure",
    ...devices["Desktop Chrome"],
    permissions: ["microphone"],
    launchOptions: {
      // Ambientes com Chromium pré-instalado em versão diferente do @playwright/test apontam aqui.
      executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined,
      args: ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"],
    },
  },
  webServer: {
    command: `npm run dev -- --port ${porta}`,
    url: `${baseURL}/login`,
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
