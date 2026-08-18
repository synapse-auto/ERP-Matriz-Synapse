import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/lib/config/textos-provider", () => ({
  useTextos: () => ({
    auth: {
      mostrarSenha: "Mostrar senha",
      ocultarSenha: "Ocultar senha",
    },
  }),
}));

import { PasswordInput } from "./password-input";

describe("PasswordInput", () => {
  it("alterna entre type password e text, preservando o valor", () => {
    render(<PasswordInput defaultValue="minhaSenhaSecreta" />);

    const input = screen.getByDisplayValue("minhaSenhaSecreta") as HTMLInputElement;
    expect(input.type).toBe("password");

    const botaoMostrar = screen.getByRole("button", { name: "Mostrar senha" });
    fireEvent.click(botaoMostrar);

    expect(input.type).toBe("text");
    expect(input.value).toBe("minhaSenhaSecreta");
    expect(screen.getByRole("button", { name: "Ocultar senha" })).toBeInTheDocument();

    const botaoOcultar = screen.getByRole("button", { name: "Ocultar senha" });
    fireEvent.click(botaoOcultar);

    expect(input.type).toBe("password");
    expect(input.value).toBe("minhaSenhaSecreta");
  });
});
