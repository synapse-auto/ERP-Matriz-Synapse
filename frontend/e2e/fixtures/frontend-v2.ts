import type { Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import type { VisaoGeralDashboard } from "../../src/lib/dashboard/types";

interface OpcoesFrontendV2 {
  features?: string[];
  quantidade?: number;
  tema?: Record<string, unknown>;
  dashboard?: VisaoGeralDashboard;
}

export const temaFixture = JSON.parse(readFileSync(resolve("../backend/crm-app/src/main/resources/tema.json"), "utf8"));

// Dados sintéticos exclusivos da suíte, sem nomes, números ou mensagens do mockup.
const usuarioId = "a0000000-0000-4000-8000-000000000001";
const instante = new Date().toISOString();
export const cartoesFixture = Array.from({ length: 15 }, (_, indice) => ({
  atendimentoId: `b0000000-0000-4000-8000-${String(indice + 1).padStart(12, "0")}`,
  leadId: `c0000000-0000-4000-8000-${String(indice + 1).padStart(12, "0")}`,
  leadNome: `Contato de verificação ${indice + 1}`,
  leadFotoUrl: null, leadEmpresa: null, canalTipo: "WHATSAPP",
  etapaId: null, etapaNome: null, etapaCor: null,
  status: "EM_ATENDIMENTO", atendenteId: usuarioId, atendenteNome: "Pessoa de teste",
  ultimaMensagemPreview: "Mensagem sintética para validar a apresentação.",
  ultimaMensagemRemetenteTipo: "LEAD", ultimaMensagemEm: instante,
  ultimaMensagemDoLeadEm: instante, naoLidas: indice % 3,
}));

export async function prepararFrontendV2(page: Page, opcoes: OpcoesFrontendV2 = {}) {
  const { features = [], quantidade = 15, tema: temaDaFixture = temaFixture, dashboard } = opcoes;
  const cartoes = cartoesFixture;
  await page.request.post("http://127.0.0.1:38981/__fixture/tema", { data: temaDaFixture });
  const chamadas: string[] = [];
  const requisicoes: string[] = [];
  const desconhecidas: string[] = [];
  await page.context().addCookies([{
    name: "synapse_refresh", value: "fixture-local-sem-autenticacao-real",
    url: "http://127.0.0.1:3105", httpOnly: true,
  }]);
  await page.route("**/api/auth/refresh", (rota) => rota.fulfill({
    json: {
      accessToken: ["fixture", Buffer.from(JSON.stringify({ sub: usuarioId, papel: "ATENDENTE" })).toString("base64url"), "unsigned"].join("."),
      expiraEmSegundos: 3600,
    },
  }));
  await page.routeWebSocket(/\/ws/, (ws) => {
    ws.onMessage((frame) => {
      if (String(frame).startsWith("CONNECT")) ws.send("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0");
    });
  });
  await page.route("**/api/v1/**", async (rota) => {
    const url = new URL(rota.request().url());
    const caminho = url.pathname.replace("/api/v1", "");
    chamadas.push(caminho);
    requisicoes.push(url.pathname + url.search);
    const cartao = cartoes.find((item) => caminho.includes(item.atendimentoId) || caminho.includes(item.leadId)) ?? cartoes[0];
    let corpo: unknown;
    if (caminho === "/config/features") corpo = features;
    else if (caminho === "/dashboard/visao-geral" && dashboard) corpo = dashboard;
    else if (caminho === "/config/tema") corpo = temaDaFixture;
    else if (caminho === "/config/logo") { await rota.fulfill({ status: 204 }); return; }
    else if (caminho === "/leads/integracoes/darwin") corpo = { habilitado: false };
    else if (caminho === "/gestao/permissoes/minhas") corpo = {
      usuarioId, papel: "ATENDENTE", revisao: "fixture",
      capacidades: Object.fromEntries(["clientes.acessar", "atendimentos.ver", "atendimentos.responder", "contatos.ver", "contatos.editar", ...(dashboard ? ["dashboard.ver"] : [])].map((chave) => [chave, { permitido: true, alcance: "MEUS" }])),
    };
    else if (caminho === "/me") corpo = { id: usuarioId, nome: "Pessoa de teste", papel: "ATENDENTE", presenca: "ONLINE", usuarioTeste: false };
    else if (caminho === "/config/canal") corpo = { exigeTemplateForaDaJanela: false, gerenciaTemplates: false };
    else if (caminho === "/atendimentos/configuracao-composer") corpo = { tempoNotificacaoSegundos: 8 };
    else if (caminho === "/atendimentos/inbox") corpo = { itens: cartoes.slice(0, quantidade), proximoCursor: null };
    else if (caminho === "/atendimentos/contagem") corpo = { TODOS: quantidade, ATIVOS: quantidade, PENDENTES: 0, POTENCIAIS: 0, FINALIZADOS: 0 };
    else if (caminho.endsWith("/estado")) corpo = { cartao, versao: 1, participantes: [], usuarioAtualEhResponsavel: true, usuarioAtualParticipa: false, podeEnviar: true };
    else if (caminho.endsWith("/leitura")) { await rota.fulfill({ status: 204 }); return; }
    else if (caminho.endsWith("/mensagens")) corpo = { mensagens: [{
      id: "d0000000-0000-4000-8000-000000000001", atendimentoId: cartao.atendimentoId,
      remetenteTipo: "LEAD", remetenteId: null, remetenteNome: null, tipo: "TEXTO",
      conteudo: "Mensagem sintética para verificar o histórico.", midiaUrl: null,
      midiaMetadados: null, opcoes: null, statusEntrega: "ENTREGUE", erroEntrega: null, enviadoEm: instante,
    }], proximoCursor: null };
    else if (caminho.endsWith("/informacoes-do-chatbot")) corpo = { habilitado: false, cards: [], proximoCursor: null };
    else if (caminho.endsWith("/resumo-ia") || caminho.endsWith("/pedido-entrada/meu") || caminho.endsWith("/avaliacao")) corpo = null;
    else if (caminho === "/usuarios/me/presenca") corpo = { status: "ONLINE" };
    else if (/^\/leads\/[^/]+$/.test(caminho)) corpo = {
      id: cartao.leadId, nome: cartao.leadNome, fotoUrl: null, telefone: null,
      email: null, cpf: null, empresa: null, codigo: null, localizacao: null,
      canalOrigemId: null, status: "EM_ATENDIMENTO", etapaAtendimentoId: null,
      atendenteResponsavelId: usuarioId, notas: null, resumoIa: null, resumoIaAtualizadoEm: null,
      numAtendimentos: 1, numMensagens: 1, criadoEm: instante, dadosCustomizados: {},
    };
    else if (caminho.endsWith("/midias/contagem")) corpo = { imagens: 0, videos: 0, audios: 0, documentos: 0, total: 0 };
    else if (caminho === "/lembretes") corpo = { lembretes: [], total: 0 };
    else if (caminho === "/mensagens-programadas") corpo = { mensagens: [], total: 0 };
    else if (["/etapas", "/tags", "/campos-customizados", "/usuarios", "/mensagens-rapidas"].includes(caminho) || caminho.endsWith("/tags") || caminho.endsWith("/participantes") || caminho.endsWith("/pedidos-entrada")) corpo = [];
    else {
      desconhecidas.push(caminho);
      await rota.fulfill({ status: 501, json: { detail: "Rota não prevista pela fixture local." } });
      return;
    }
    await rota.fulfill({ json: corpo });
  });
  return { chamadas, requisicoes, desconhecidas };
}
