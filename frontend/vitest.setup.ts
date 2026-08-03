import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

import "@testing-library/jest-dom/vitest";

// Sem `globals: true` no vitest.config.ts, o auto-cleanup do Testing Library não se registra
// sozinho — sem isto, o DOM de um teste vaza para o próximo dentro do mesmo arquivo.
afterEach(() => {
  cleanup();
});
