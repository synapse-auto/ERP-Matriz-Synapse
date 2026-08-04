import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { TextosProvider } from "@/lib/config/textos-provider";
import type { Textos } from "@/lib/config/schema";

import { StatusEntregaIcone } from "./status-entrega";

type ParcialProfundo<T> = T extends object
  ? { [Chave in keyof T]?: ParcialProfundo<T[Chave]> }
  : T;

const TEXTOS_FIXTURE = {
  app: { nome: "", marca: "", subtitulo: "" },
  menu: { grupoMenu: "", grupoGestao: "", itens: {} },
  rodape: {
    trocarConta: "",
    sair: "",
    presenca: { rotulo: "", online: "", ausente: "", offline: "" },
  },
  login: {
    titulo: "",
    subtitulo: "",
    campoEmail: "",
    campoSenha: "",
    botaoEntrar: "",
    entrando: "",
    erroCredenciais: "",
    erroGenerico: "",
  },
  estados: {
    carregando: "",
    vazio: "",
    erroGenerico: "",
    tentarNovamente: "",
    emConstrucao: "",
    sessaoExpirada: "",
  },
  atendimentos: {
    visoes: { ativos: "", pendentes: "", potenciais: "", todos: "" },
    filtros: { etapa: "", status: "", tag: "", atendente: "", busca: "" },
    cartao: { semAtendente: "", vazio: "" },
    cabecalho: { atendidoPor: "", semAtendente: "", transferir: "", finalizar: "", buscar: "" },
    transferir: {
      titulo: "",
      descricao: "",
      devolverParaIa: "",
      assumirParaMim: "",
      confirmar: "",
      cancelar: "",
      sucesso: "",
      erro: "",
    },
    finalizar: { titulo: "", descricao: "", confirmar: "", cancelar: "", sucesso: "", erro: "" },
    mensagem: {
      status: {
        pendente: "Enviando",
        enviado: "Enviado",
        entregue: "Entregue",
        lido: "Lido",
        falhou: "Falha ao enviar",
      },
      reenviar: "Reenviar",
      carregarAnteriores: "",
      carregandoAnteriores: "",
    },
    composer: {
      placeholder: "",
      enviar: "",
      anexo: "",
      anexoIndisponivel: "",
      anexoSelecionar: "",
      anexoRemover: "",
      anexoEnviando: "",
      anexoErro: "",
      anexoLegendaPlaceholder: "",
      anexoTipoNaoPermitido: "",
      anexoExcedeuLimite: "",
      emoji: "",
      janelaFechadaTitulo: "",
      janelaFechadaDescricao: "",
      semTemplates: "",
      agendar: "",
      mensagensRapidas: "",
    },
    tempoReal: { reconectando: "", conversaEncerrada: "" },
    media: { imagem: "", audio: "", documento: "", baixar: "" },
  },
} satisfies ParcialProfundo<Textos>;

function renderComTextos(ui: React.ReactElement) {
  return render(
    <TextosProvider textos={TEXTOS_FIXTURE as Textos}>{ui}</TextosProvider>,
  );
}

describe("StatusEntregaIcone", () => {
  it.each([
    ["PENDENTE", "Enviando"],
    ["ENVIADO", "Enviado"],
    ["ENTREGUE", "Entregue"],
    ["LIDO", "Lido"],
  ] as const)("renderiza o ícone certo para %s", (status, rotulo) => {
    renderComTextos(<StatusEntregaIcone status={status} />);
    expect(screen.getByTitle(rotulo)).toBeInTheDocument();
  });

  it("FALHOU mostra texto de erro e o botão de reenviar, que aciona o callback", async () => {
    const onReenviar = vi.fn();
    renderComTextos(<StatusEntregaIcone status="FALHOU" onReenviar={onReenviar} />);

    expect(screen.getByText("Falha ao enviar")).toBeInTheDocument();
    screen.getByRole("button", { name: "Reenviar" }).click();

    expect(onReenviar).toHaveBeenCalledTimes(1);
  });

  it("FALHOU sem callback não mostra o botão de reenviar", () => {
    renderComTextos(<StatusEntregaIcone status="FALHOU" />);
    expect(screen.queryByRole("button", { name: "Reenviar" })).not.toBeInTheDocument();
  });
});
