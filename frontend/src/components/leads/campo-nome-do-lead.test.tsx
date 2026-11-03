import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { CampoNomeDoLead } from "./campo-nome-do-lead";

const salvar = vi.hoisted(() => ({ mutate: vi.fn(), isPending: false }));
vi.mock("@/lib/lead/use-painel-lead", () => ({ useSalvarFicha: () => salvar }));
vi.mock("@/lib/gestao/use-capacidades", () => ({ useCapacidades: () => ({ pode: () => true }) }));
vi.mock("@/lib/config/textos-provider", () => ({ useTextos: () => ({ painelLead: { dados: { nome: "Nome", nomeInvalido: "Nome inválido" } } }) }));
beforeEach(() => salvar.mutate.mockReset());

it("reflete atualização confirmada na ficha sem F5 e sem chamar a mutation manual", () => {
  const tela = render(<CampoNomeDoLead leadId="lead" valorAtual="Atual" />);
  tela.rerender(<CampoNomeDoLead leadId="lead" valorAtual="Encontrado e confirmado" />);
  expect(screen.getByLabelText("Nome")).toHaveValue("Encontrado e confirmado");
  expect(salvar.mutate).not.toHaveBeenCalled();
});
it("refetch não apaga rascunho manual; sucesso retoma o valor persistido", () => {
  const tela = render(<CampoNomeDoLead leadId="lead" valorAtual="Atual" />);
  fireEvent.change(screen.getByLabelText("Nome"), { target: { value: "Rascunho manual" } });
  tela.rerender(<CampoNomeDoLead leadId="lead" valorAtual="Atualizado externamente" />);
  expect(screen.getByLabelText("Nome")).toHaveValue("Rascunho manual");
  fireEvent.blur(screen.getByLabelText("Nome"));
  expect(salvar.mutate).toHaveBeenCalledWith({ nome: "Rascunho manual" }, expect.any(Object));
  tela.rerender(<CampoNomeDoLead leadId="lead" valorAtual="Rascunho manual" />);
  act(() => salvar.mutate.mock.calls[0][1].onSuccess());
  expect(screen.getByLabelText("Nome")).toHaveValue("Rascunho manual");
  tela.rerender(<CampoNomeDoLead leadId="lead" valorAtual="Confirmação seguinte" />);
  expect(screen.getByLabelText("Nome")).toHaveValue("Confirmação seguinte");
});
