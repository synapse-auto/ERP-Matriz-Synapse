import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { limparEstadosRecuperaveis, useEstadoRecuperavel } from "./estado-recuperavel";
import { useAuthStore } from "@/lib/auth/auth-store";

function Editor({ chave }: { chave: string }) {
  const [texto, setTexto] = useEstadoRecuperavel(chave, "");
  return <input aria-label="Rascunho" value={texto} onChange={(e) => setTexto(e.target.value)} />;
}
beforeEach(limparEstadosRecuperaveis);
describe("estado recuperável só em memória", () => {
  it("remontagem recupera rascunho sem enviá-lo; outra identidade não recebe o texto", () => {
    const tela = render(<Editor chave="usuario-a:atendimento-a" />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Rascunho sintético" } });
    tela.unmount();
    const outra = render(<Editor chave="usuario-b:atendimento-a" />);
    expect(screen.getByRole("textbox")).toHaveValue("");
    outra.unmount();
    render(<Editor chave="usuario-a:atendimento-a" />);
    expect(screen.getByRole("textbox")).toHaveValue("Rascunho sintético");
  });
  it("logout elimina conteúdo recuperável", () => {
    const tela = render(<Editor chave="usuario-a:atendimento-a" />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Privado" } });
    tela.unmount();
    useAuthStore.getState().limparSessao();
    render(<Editor chave="usuario-a:atendimento-a" />);
    expect(screen.getByRole("textbox")).toHaveValue("");
  });
});
