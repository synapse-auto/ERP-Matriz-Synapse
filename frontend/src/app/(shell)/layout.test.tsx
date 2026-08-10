import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/headers", () => ({
  cookies: async () => ({ has: () => true }),
}));

vi.mock("next/navigation", () => ({ redirect: vi.fn() }));

vi.mock("@/components/shell/sidebar", () => ({
  Sidebar: () => <aside>Menu</aside>,
}));

import ShellLayout from "./layout";

describe("superfície compartilhada do shell", () => {
  it("envolve todas as páginas em painel temático com margem de 20px e raio de 16px", async () => {
    render(await ShellLayout({ children: <div>Conteúdo</div> }));

    const superficie = screen.getByText("Conteúdo").closest("main");
    expect(superficie).toHaveClass("bg-card", "rounded-lg", "shadow-sm");
    expect(superficie?.parentElement).toHaveClass("p-5");
    expect(superficie?.closest('[data-slot="page-canvas"]')).toHaveClass(
      "bg-[var(--fundo-canvas)]",
    );
  });
});
