import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Textos } from "@/lib/config/schema";

import { DialogoImportacaoLeads } from "./dialogo-importacao-leads";

const visualizar = vi.fn();
const confirmar = vi.fn();

vi.mock("@/lib/agenda/api", () => ({
  visualizarImportacaoLeads: (...args: unknown[]) => visualizar(...args),
  confirmarImportacaoLeads: (...args: unknown[]) => confirmar(...args),
}));

const textos = {
  titulo: "Importar leads (CSV)",
  descricao: "Adicione contatos em lote à base",
  arraste: "Arraste um arquivo .csv aqui",
  colunas: "Colunas: nome, empresa, telefone, CNPJ/CPF, cidade, etapa, tags",
  selecionarArquivo: "Selecionar arquivo",
  baixarModelo: "Baixar modelo",
  cancelar: "Cancelar",
  importar: "Importar",
  importando: "Importando...",
  preview: "Prévia da importação",
  linhas: "Linhas",
  validas: "Novas",
  jaExistiam: "Já existentes",
  recusadas: "Recusadas",
  arquivoInvalido: "Selecione um arquivo CSV.",
  erro: "Erro de prévia",
  erroImportar: "Erro de importação",
  modeloArquivo: "modelo.csv",
} as Textos["agenda"]["importacao"];

function renderDialog() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <DialogoImportacaoLeads aberto onAbertoChange={vi.fn()} textos={textos} />
    </QueryClientProvider>,
  );
}

describe("DialogoImportacaoLeads", () => {
  beforeEach(() => {
    visualizar.mockReset();
    confirmar.mockReset();
    visualizar.mockResolvedValue({ totalDeLinhas: 1, validas: 1, jaExistiam: 0, recusadas: [] });
  });

  it("mostra a prévia sem enviar automaticamente ao escolher um arquivo", async () => {
    renderDialog();
    expect(screen.getByRole("button", { name: "Importar" })).toBeDisabled();
    const arquivo = new File(["nome,telefone\nMaria,5561999999999"], "leads.csv", { type: "text/csv" });
    fireEvent.change(document.querySelector('input[type="file"]')!, { target: { files: [arquivo] } });

    await waitFor(() => expect(screen.getByText("Prévia da importação")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Importar" })).toBeEnabled();
    expect(visualizar).toHaveBeenCalledWith(arquivo, expect.anything());
    expect(confirmar).not.toHaveBeenCalled();
  });

  it("mantém a área de seleção íntegra antes do upload", () => {
    renderDialog();
    expect(screen.getByText("Arraste um arquivo .csv aqui")).toBeInTheDocument();
  });
});
