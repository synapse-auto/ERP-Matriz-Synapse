import { expect, test, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const tema = JSON.parse(readFileSync(resolve("../backend/crm-app/src/main/resources/tema.json"), "utf8"));

// Dados sintéticos exclusivos da suíte, sem nomes, números ou mensagens do mockup.
const usuarioId = "a0000000-0000-4000-8000-000000000001";
const instante = new Date().toISOString();
const cartoes = Array.from({ length: 15 }, (_, indice) => ({
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

async function preparar(page: Page, habilitado: boolean, quantidade = 15, temaDaFixture = tema) {
  await page.request.post("http://127.0.0.1:38981/__fixture/tema", { data: temaDaFixture });
  const chamadas: string[] = [];
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
    const cartao = cartoes.find((item) => caminho.includes(item.atendimentoId) || caminho.includes(item.leadId)) ?? cartoes[0];
    let corpo: unknown;
    if (caminho === "/config/features") corpo = habilitado ? ["atendimentos_layout_v2"] : [];
    else if (caminho === "/config/tema") corpo = temaDaFixture;
    else if (caminho === "/config/logo") { await rota.fulfill({ status: 204 }); return; }
    else if (caminho === "/leads/integracoes/darwin") corpo = { habilitado: false };
    else if (caminho === "/gestao/permissoes/minhas") corpo = {
      usuarioId, papel: "ATENDENTE", revisao: "fixture",
      capacidades: Object.fromEntries(["clientes.acessar", "atendimentos.ver", "atendimentos.responder", "contatos.ver", "contatos.editar"].map((chave) => [chave, { permitido: true, alcance: "MEUS" }])),
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
  return { chamadas, desconhecidas };
}

async function verificarGeometria(page: Page) {
  const geometria = await page.evaluate(() => {
    const cabecalho = document.querySelector('[data-slot="cabecalho-conversa"]');
    const coluna = cabecalho?.parentElement?.getBoundingClientRect();
    return { excesso: document.documentElement.scrollWidth - innerWidth, larguraConversa: coluna?.width };
  });
  expect(geometria.excesso).toBeLessThanOrEqual(1);
  expect(geometria.larguraConversa).toBeGreaterThanOrEqual(320);
}

for (const largura of [1440, 1040, 720, 390]) {
  test(`v2: lista, conversa e composer utilizáveis em ${largura}px`, async ({ page }) => {
    await page.setViewportSize({ width: largura, height: largura === 390 ? 844 : 900 });
    const fixture = await preparar(page, true);
    const erros: string[] = [];
    page.on("pageerror", (erro) => erros.push(erro.message));
    await page.goto("/atendimentos");
    await expect(page.locator('[data-slot="pagina-atendimentos"]')).toHaveAttribute("data-layout", "v2");
    await page.getByRole("button").filter({ hasText: cartoes[0].leadNome }).first().click();
    await expect(page.locator('[data-slot="cabecalho-conversa"]')).toBeVisible();
    await expect(page.locator('[data-slot="composer"] textarea')).toBeVisible();
    await page.waitForLoadState("networkidle");
    await verificarGeometria(page);
    await page.screenshot({ path: `../docs/refatoracao-frontend/implementacao/atendimentos-v2-${largura}.png` });
    if (largura === 390) {
      await page.setViewportSize({ width: 390, height: 520 });
      await verificarGeometria(page);
      const caixa = await page.locator('[data-slot="composer"] textarea').boundingBox();
      expect(caixa!.y + caixa!.height).toBeLessThanOrEqual(520);
    }
    expect(fixture.desconhecidas).toEqual([]);
    expect(erros.filter((erro) => !erro.includes("negative time stamp"))).toEqual([]);
  });
}

test("sem flag mantém legado; mais linhas não acrescentam consultas por contato", async ({ browser }) => {
  const resultados: string[][] = [];
  for (const [habilitado, quantidade] of [[false, 1], [true, 15]] as const) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const fixture = await preparar(page, habilitado, quantidade);
    await page.goto("/atendimentos");
    await expect(page.locator('[data-slot="pagina-atendimentos"]')).toHaveAttribute("data-layout", habilitado ? "v2" : "legado");
    await expect(page.locator('[data-slot="cartao-conversa"]')).toHaveCount(quantidade);
    await page.waitForLoadState("networkidle");
    resultados.push([...fixture.chamadas].sort());
    if (!habilitado) await page.screenshot({ path: "../docs/refatoracao-frontend/implementacao/atendimentos-legado-1440.png" });
    expect(fixture.desconhecidas).toEqual([]);
    await page.close();
  }
  expect([...new Set(resultados[1])]).toEqual([...new Set(resultados[0])]);
  // A hidratação de /me em Next dev pode repetir uma leitura durante a troca do cache
  // de sessão. A listagem deve conservar exatamente as mesmas chamadas, sem N+1.
  expect(resultados[1].filter((caminho) => caminho.startsWith("/atendimentos")))
    .toEqual(resultados[0].filter((caminho) => caminho.startsWith("/atendimentos")));
});

test("v2 acompanha uma configuração escura da instância sem clarear os painéis", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  // Paleta sintética apenas da fixture: nenhuma alteração no tema ou no catálogo de produção.
  await preparar(page, true, 15, {
    ...tema, fundoApp: "#121212", fundoSuperficie: "#202020", fundoSutil: "#292929",
    textoForte: "#FAFAFA", textoPadrao: "#FAFAFA", textoSuave: "#B5B5B5",
    borda: "#4A4A4A", corPrimariaSuave: "#25364A",
    corPrimaria: "#60A5FA", corPrimariaHover: "#93C5FD", corPrimariaTexto: "#0F172A",
  });
  await page.goto("/atendimentos");
  await expect(page.locator('[data-slot="pagina-atendimentos"]')).toHaveAttribute("data-layout", "v2");
  await page.getByRole("button").filter({ hasText: cartoes[0].leadNome }).first().click();
  await expect(page.locator('[data-slot="composer"] textarea')).toBeVisible();
  await expect(page.locator('[data-slot="lista-conversas"]')).toHaveCSS("background-color", "rgb(32, 32, 32)");
  const cores = await page.locator('[data-slot="lista-conversas"]').evaluate((lista) => {
    const estilo = getComputedStyle(lista);
    return { fundo: estilo.backgroundColor, esperado: getComputedStyle(document.documentElement).getPropertyValue("--card").trim() };
  });
  expect(cores.fundo).not.toBe("rgb(255, 255, 255)");
  expect(cores.esperado).not.toBe("");
  await verificarGeometria(page);
  await page.screenshot({ path: "../docs/refatoracao-frontend/implementacao/atendimentos-v2-escuro-1440.png" });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('[data-slot="cabecalho-conversa"]')).toBeVisible();
  await verificarGeometria(page);
  await page.screenshot({ path: "../docs/refatoracao-frontend/implementacao/atendimentos-v2-escuro-390.png" });
});

for (const variante of ["a-sidebar-vinho", "b-sidebar-branca"]) {
  test(`v2 preserva o tema Disbrel ${variante}`, async ({ page }) => {
    const temaDaInstancia = JSON.parse(readFileSync(resolve(`../docker/provisionamento/instancias/disbrel/tema-${variante}.json`), "utf8"));
    await page.setViewportSize({ width: 1440, height: 900 });
    await preparar(page, true, 15, temaDaInstancia);
    await page.goto("/atendimentos");
    await expect(page.locator('[data-slot="pagina-atendimentos"]')).toHaveAttribute("data-layout", "v2");
    await page.getByRole("button").filter({ hasText: cartoes[0].leadNome }).first().click();
    await expect(page.locator('[data-slot="composer"] textarea')).toBeVisible();
    await verificarGeometria(page);
    const primaria = await page.locator('[data-slot="pagina-atendimentos"]').evaluate((grade) => getComputedStyle(grade).getPropertyValue("--primary").trim());
    expect(primaria.toLowerCase()).toBe(temaDaInstancia.corPrimaria.toLowerCase());
    await page.screenshot({ path: `../docs/refatoracao-frontend/implementacao/atendimentos-v2-disbrel-${variante}.png` });
  });
}
