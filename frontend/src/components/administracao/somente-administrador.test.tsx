import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const authState = vi.hoisted(() => ({ papel: "ADMINISTRADOR" as string | null }));

vi.mock("@/lib/auth/auth-store", () => ({
  useAuthStore: (seletor: (estado: typeof authState) => unknown) => seletor(authState),
}));

import { SomenteAdministrador } from "./somente-administrador";

describe("SomenteAdministrador", () => {
  beforeEach(() => {
    authState.papel = "ADMINISTRADOR";
  });

  it("renderiza os filhos para ADMINISTRADOR", () => {
    render(<SomenteAdministrador><span>preenchimento</span></SomenteAdministrador>);

    expect(screen.getByText("preenchimento")).toBeInTheDocument();
  });

  it.each(["ATENDENTE", "SUBGESTOR", "GESTOR", null])(
    "esconde os filhos para papel %s",
    (papel) => {
      authState.papel = papel;
      render(<SomenteAdministrador><span>preenchimento</span></SomenteAdministrador>);

      expect(screen.queryByText("preenchimento")).not.toBeInTheDocument();
    },
  );
});
