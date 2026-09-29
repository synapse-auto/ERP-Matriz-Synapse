import { expect, test, type APIRequestContext, type Locator, type Page } from "@playwright/test";

/**
 * Gestão de templates (criar, editar, excluir) no navegador real, nas duas superfícies: a página
 * "Templates WhatsApp" e o modal de templates dentro do atendimento.
 *
 * Nunca toca a Meta: o backend roda com o adaptador de produção (`WHATSAPP_PROVEDOR=meta-cloud`)
 * apontado para o stub `e2e/fixtures/graph-meta-stub.mjs`, que registra cada chamada recebida. O
 * que se afirma aqui é a cadeia inteira: clique → HTTP → autorização → adaptador → provedor.
 *
 * Pré-requisitos (ver docs/48-templates-gestao-auditoria.md):
 *   node e2e/fixtures/graph-meta-stub.mjs
 *   backend dev com WHATSAPP_URL_BASE=http://127.0.0.1:8089/v21.0, WHATSAPP_CONTA_NEGOCIO=waba-e2e,
 *     WHATSAPP_NUMERO=phone-e2e
 *   frontend em :3000 com NEXT_PUBLIC_API_URL=http://localhost:8080
 */
const API = process.env.PLAYWRIGHT_API_URL ?? "http://localhost:8080";
const STUB = process.env.PLAYWRIGHT_GRAPH_STUB_URL ?? "http://127.0.0.1:8089";

const GESTOR = { email: "gestor@dev.local", senha: "gestor123" };
const ANA = { email: "ana@dev.local", senha: "atendente123" };
const CONTATO = { nome: "Contato E2E Templates", telefone: "83999990001" };

type Chamada = { metodo: string; caminho: string; query: Record<string, string>; corpo: unknown };


test.beforeEach(async ({ request }) => {
  const resposta = await request.post(`${STUB}/__controle/reset`).catch(() => null);
  test.skip(!resposta?.ok(), "stub da Graph API não está no ar");
});

async function chamadas(request: APIRequestContext, metodo?: string): Promise<Chamada[]> {
  const todas = (await (await request.get(`${STUB}/__controle/chamadas`)).json()) as Chamada[];
  return todas.filter((c) => c.caminho !== "/phone-e2e" && (!metodo || c.metodo === metodo));
}

async function falhar(request: APIRequestContext, operacao: string, status: number | null, atrasoMs = 0) {
  await request.post(`${STUB}/__controle/falhar`, { data: { operacao, status, atrasoMs } });
}

async function entrar(page: Page, { email, senha }: { email: string; senha: string }) {
  await page.goto("/login");
  await page.getByLabel(/e-?mail/i).fill(email);
  await page.locator('input[type="password"]').fill(senha);
  await page.getByRole("button", { name: /entrar/i }).click();
  await expect(page).toHaveURL(/\/atendimentos/);
}

async function tokenDe(request: APIRequestContext, { email, senha }: { email: string; senha: string }) {
  const resposta = await request.post(`${API}/api/v1/auth/login`, { data: { email, senha } });
  expect(resposta.ok()).toBeTruthy();
  return (await resposta.json()).accessToken as string;
}

async function abrirPagina(page: Page) {
  await entrar(page, GESTOR);
  await page.goto("/templates-whatsapp");
  await expect(page.getByText("retorno_orcamento_e2e", { exact: true })).toBeVisible();
}

async function abrirModalDoAtendimento(page: Page, request: APIRequestContext): Promise<Locator> {
  const token = await tokenDe(request, GESTOR);
  const contato = await request.post(`${API}/api/v1/atendimentos/novo-contato`, {
    headers: { Authorization: `Bearer ${token}` },
    data: CONTATO,
  });
  expect(contato.ok()).toBeTruthy();
  await entrar(page, GESTOR);
  await page.getByText(CONTATO.nome).first().click();
  await page.getByRole("button", { name: "Nova mensagem" }).click();
  const modal = page.getByRole("dialog", { name: "Enviar template" });
  await expect(modal.getByText("retorno_orcamento_e2e", { exact: true })).toBeVisible();
  return modal;
}

function confirmacao(page: Page) {
  return page.getByRole("dialog", { name: "Excluir template na Meta?" });
}

// --- Exclusão: página ----------------------------------------------------------------------------

test.describe("página Templates WhatsApp — exclusão", () => {
  test("confirmar envia exatamente um DELETE para o template escolhido e o remove da lista", async ({ page, request }) => {
    await abrirPagina(page);
    await page.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
    await confirmacao(page).getByRole("button", { name: "Excluir na Meta" }).click();

    await expect(confirmacao(page)).toBeHidden();
    await expect(page.getByText("retorno_orcamento_e2e", { exact: true })).toBeHidden();
    const deletes = await chamadas(request, "DELETE");
    expect(deletes).toHaveLength(1);
    expect(deletes[0].query).toEqual({ hsm_id: "tpl-e2e-2", name: "retorno_orcamento_e2e" });
  });

  test("cancelar não envia DELETE", async ({ page, request }) => {
    await abrirPagina(page);
    await page.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
    await confirmacao(page).getByRole("button", { name: "Cancelar" }).click();

    await expect(confirmacao(page)).toBeHidden();
    await expect(page.getByText("retorno_orcamento_e2e", { exact: true })).toBeVisible();
    expect(await chamadas(request, "DELETE")).toHaveLength(0);
  });

  for (const caso of [
    { nome: "recusa do provedor (API 422)", status: 400, texto: /recusou/i },
    { nome: "provedor fora do ar (API 503)", status: 500, texto: /indisponível/i },
  ]) {
    test(`${caso.nome}: mantém o diálogo e o item e mostra o erro`, async ({ page, request }) => {
      await falhar(request, "excluir", caso.status);
      await abrirPagina(page);
      await page.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
      await confirmacao(page).getByRole("button", { name: "Excluir na Meta" }).click();

      await expect(confirmacao(page).getByRole("alert")).toHaveText(caso.texto);
      await expect(confirmacao(page).getByRole("button", { name: "Excluir na Meta" })).toBeEnabled();
      await expect(page.getByText("retorno_orcamento_e2e", { exact: true })).toBeVisible();
      expect(await chamadas(request, "DELETE")).toHaveLength(1);
    });
  }

  test("permissão revogada no servidor (API 403): mantém o item e mostra o erro", async ({ page, request }) => {
    await abrirPagina(page);
    // A revogação entre abrir a tela e confirmar só é observável pela resposta: simula-se o 403
    // exatamente como o backend o devolve (RFC 7807).
    await page.route("**/api/v1/whatsapp/templates/**", (rota) =>
      rota.request().method() === "DELETE"
        ? rota.fulfill({
            status: 403,
            contentType: "application/problem+json",
            body: JSON.stringify({ status: 403, title: "Forbidden", detail: "Access Denied" }),
          })
        : rota.continue(),
    );
    await page.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
    await confirmacao(page).getByRole("button", { name: "Excluir na Meta" }).click();

    await expect(confirmacao(page).getByRole("alert")).toHaveText(/permissão/i);
    await expect(page.getByText("retorno_orcamento_e2e", { exact: true })).toBeVisible();
    expect(await chamadas(request, "DELETE")).toHaveLength(0);
  });

  test("com o DELETE em voo não dá para fechar a confirmação e reabrir para outro template", async ({ page, request }) => {
    await falhar(request, "excluir", null, 2000);
    await abrirPagina(page);
    await page.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
    await confirmacao(page).getByRole("button", { name: "Excluir na Meta" }).click();

    await expect(confirmacao(page).getByRole("button", { name: "Cancelar" })).toBeDisabled();
    await page.keyboard.press("Escape");
    await expect(confirmacao(page)).toBeVisible();
    await expect(confirmacao(page)).toBeHidden({ timeout: 10_000 });
    expect(await chamadas(request, "DELETE")).toHaveLength(1);
  });

  test("clique repetido durante o envio não duplica o DELETE", async ({ page, request }) => {
    await falhar(request, "excluir", null, 1500);
    await abrirPagina(page);
    await page.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
    const confirmar = confirmacao(page).getByRole("button", { name: "Excluir na Meta" });
    await confirmar.click();
    await confirmar.click({ force: true, timeout: 1000 }).catch(() => undefined);
    await confirmar.click({ force: true, timeout: 1000 }).catch(() => undefined);

    await expect(confirmacao(page)).toBeHidden({ timeout: 10_000 });
    expect(await chamadas(request, "DELETE")).toHaveLength(1);
  });
});

// --- Exclusão: modal do atendimento -------------------------------------------------------------

test.describe("modal de templates no atendimento — exclusão", () => {
  test("confirmar envia exatamente um DELETE, remove o item e mantém o modal de envio aberto", async ({ page, request }) => {
    const modal = await abrirModalDoAtendimento(page, request);
    await modal.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
    await confirmacao(page).getByRole("button", { name: "Excluir na Meta" }).click();

    await expect(confirmacao(page)).toBeHidden();
    await expect(modal).toBeVisible();
    await expect(modal.getByText("retorno_orcamento_e2e", { exact: true })).toBeHidden();
    await expect(modal.getByText("boas_vindas_e2e")).toBeVisible();
    const deletes = await chamadas(request, "DELETE");
    expect(deletes).toHaveLength(1);
    expect(deletes[0].query).toEqual({ hsm_id: "tpl-e2e-2", name: "retorno_orcamento_e2e" });
  });

  test("o botão de confirmação é o elemento no topo e recebe o clique", async ({ page, request }) => {
    const modal = await abrirModalDoAtendimento(page, request);
    await modal.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
    const confirmar = confirmacao(page).getByRole("button", { name: "Excluir na Meta" });
    await expect(confirmar).toBeEnabled();

    const noTopo = await confirmar.evaluate((botao) => {
      const r = botao.getBoundingClientRect();
      const topo = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2);
      return topo === botao || botao.contains(topo);
    });
    expect(noTopo).toBe(true);
  });

  test("cancelar não envia DELETE e não fecha o modal de envio", async ({ page, request }) => {
    const modal = await abrirModalDoAtendimento(page, request);
    await modal.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
    await confirmacao(page).getByRole("button", { name: "Cancelar" }).click();

    await expect(confirmacao(page)).toBeHidden();
    await expect(modal.getByText("retorno_orcamento_e2e", { exact: true })).toBeVisible();
    expect(await chamadas(request, "DELETE")).toHaveLength(0);
  });

  test("recusa do provedor mostra o erro na confirmação e mantém o item", async ({ page, request }) => {
    await falhar(request, "excluir", 400);
    const modal = await abrirModalDoAtendimento(page, request);
    await modal.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
    await confirmacao(page).getByRole("button", { name: "Excluir na Meta" }).click();

    await expect(confirmacao(page).getByRole("alert")).toHaveText(/recusou/i);
    await expect(confirmacao(page).getByRole("button", { name: "Excluir na Meta" })).toBeEnabled();
    // Com a confirmação aberta, o modal pai fica fora da árvore de acessibilidade (diálogo modal).
    await confirmacao(page).getByRole("button", { name: "Cancelar" }).click();
    await expect(modal.getByText("retorno_orcamento_e2e", { exact: true })).toBeVisible();
    expect(await chamadas(request, "DELETE")).toHaveLength(1);
  });

  test("clique repetido durante o envio não duplica o DELETE", async ({ page, request }) => {
    await falhar(request, "excluir", null, 1500);
    const modal = await abrirModalDoAtendimento(page, request);
    await modal.getByRole("button", { name: "Excluir: retorno_orcamento_e2e" }).click();
    const confirmar = confirmacao(page).getByRole("button", { name: "Excluir na Meta" });
    await confirmar.click();
    await confirmar.click({ force: true, timeout: 1000 }).catch(() => undefined);
    await confirmar.click({ force: true, timeout: 1000 }).catch(() => undefined);

    await expect(confirmacao(page)).toBeHidden({ timeout: 10_000 });
    await expect(modal).toBeVisible();
    expect(await chamadas(request, "DELETE")).toHaveLength(1);
  });
});

// --- Criação -------------------------------------------------------------------------------------

test.describe("criação de template", () => {
  test("pedido válido chega ao provedor e aparece como pendente, nunca como aprovado", async ({ page, request }) => {
    await abrirPagina(page);
    await page.getByRole("button", { name: "Novo template" }).click();
    const formulario = page.getByRole("dialog");
    await formulario.getByLabel("Nome interno", { exact: true }).fill("aviso_e2e_novo");
    await formulario.getByRole("textbox", { name: "Texto do corpo" }).fill("Olá {{1}}, seu pedido saiu.");
    await formulario.getByRole("button", { name: "Enviar para aprovação" }).click();

    await expect(formulario).toBeHidden();
    const item = page.getByRole("listitem").filter({ hasText: "aviso_e2e_novo" });
    await expect(item).toBeVisible();
    await expect(item.getByText("Pendente")).toBeVisible();
    const posts = (await chamadas(request, "POST")).filter((c) => c.caminho === "/waba-e2e/message_templates");
    expect(posts).toHaveLength(1);
    expect(posts[0].corpo).toMatchObject({ name: "aviso_e2e_novo", language: "pt_BR", category: "UTILITY" });
  });

  test("variáveis fora de ordem bloqueiam o envio sem chamar o provedor", async ({ page, request }) => {
    await abrirPagina(page);
    await page.getByRole("button", { name: "Novo template" }).click();
    const formulario = page.getByRole("dialog");
    await formulario.getByLabel("Nome interno", { exact: true }).fill("aviso_e2e_invalido");
    await formulario.getByRole("textbox", { name: "Texto do corpo" }).fill("Olá {{2}}");

    await expect(formulario.getByRole("button", { name: "Enviar para aprovação" })).toBeDisabled();
    expect((await chamadas(request, "POST")).filter((c) => c.caminho.endsWith("message_templates"))).toHaveLength(0);
  });

  test("recusa do provedor mantém o formulário aberto com o erro e não inventa item", async ({ page, request }) => {
    await falhar(request, "criar", 400);
    await abrirPagina(page);
    await page.getByRole("button", { name: "Novo template" }).click();
    const formulario = page.getByRole("dialog");
    await formulario.getByLabel("Nome interno", { exact: true }).fill("aviso_e2e_recusado");
    await formulario.getByRole("textbox", { name: "Texto do corpo" }).fill("Olá");
    await formulario.getByRole("button", { name: "Enviar para aprovação" }).click();

    await expect(formulario.getByRole("alert")).toHaveText(/recusou/i);
    await expect(page.getByRole("listitem").filter({ hasText: "aviso_e2e_recusado" })).toHaveCount(0);
  });
});

// --- Edição --------------------------------------------------------------------------------------

test.describe("edição de template", () => {
  test("corpo válido chega ao provedor pelo ID e a lista reflete só depois do sucesso", async ({ page, request }) => {
    await abrirPagina(page);
    await page.getByRole("button", { name: "Editar: retorno_orcamento_e2e" }).click();
    const formulario = page.getByRole("dialog");
    await formulario.getByRole("textbox").fill("Orçamento revisado, {{1}}.");
    await formulario.getByRole("button", { name: "Salvar alteração" }).click();

    await expect(formulario).toBeHidden();
    await expect(page.getByText("Orçamento revisado, {{1}}.")).toBeVisible();
    const edicoes = (await chamadas(request, "POST")).filter((c) => c.caminho === "/tpl-e2e-2");
    expect(edicoes).toHaveLength(1);
    expect(edicoes[0].corpo).toMatchObject({ components: [{ type: "BODY", text: "Orçamento revisado, {{1}}." }] });
  });

  test("recusa do provedor mantém o formulário, mostra o motivo e não altera a lista", async ({ page, request }) => {
    await falhar(request, "editar", 400);
    await abrirPagina(page);
    await page.getByRole("button", { name: "Editar: retorno_orcamento_e2e" }).click();
    const formulario = page.getByRole("dialog");
    await formulario.getByRole("textbox").fill("Texto recusado");
    await formulario.getByRole("button", { name: "Salvar alteração" }).click();

    await expect(formulario.getByRole("alert")).toHaveText(/recusou/i);
    await expect(page.getByText("Seu orçamento ficou pronto.")).toBeVisible();
  });
});

// --- Permissões ----------------------------------------------------------------------------------

test("atendente não vê editar/excluir e o acesso direto à API é negado sem chegar ao provedor", async ({ page, request }) => {
  await entrar(page, ANA);
  await page.goto("/templates-whatsapp");
  await expect(page.getByText("retorno_orcamento_e2e", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: /^Excluir:/ })).toHaveCount(0);
  await expect(page.getByRole("button", { name: /^Editar:/ })).toHaveCount(0);

  const token = await tokenDe(request, ANA);
  const cabecalho = { Authorization: `Bearer ${token}` };
  const exclusao = await request.delete(`${API}/api/v1/whatsapp/templates/tpl-e2e-2?nome=retorno_orcamento_e2e`, { headers: cabecalho });
  const edicao = await request.put(`${API}/api/v1/whatsapp/templates/tpl-e2e-2`, { headers: cabecalho, data: { corpo: "x" } });
  expect(exclusao.status()).toBe(403);
  expect(edicao.status()).toBe(403);
  expect(await chamadas(request, "DELETE")).toHaveLength(0);
  expect((await chamadas(request, "POST")).filter((c) => c.caminho === "/tpl-e2e-2")).toHaveLength(0);
});

// --- Regressão: envio de template aprovado --------------------------------------------------------

test("envio de template aprovado pelo modal continua chegando ao provedor", async ({ page, request }) => {
  const modal = await abrirModalDoAtendimento(page, request);
  await modal.getByRole("button", { name: /boas_vindas_e2e/ }).first().click();
  await modal.getByRole("textbox", { name: /variável 1|Parâmetro 1/i }).fill("Maria");
  await modal.getByRole("button", { name: "Enviar este template" }).click();

  await expect
    .poll(async () => (await chamadas(request, "POST")).filter((c) => c.caminho === "/phone-e2e/messages"), { timeout: 15_000 })
    .toHaveLength(1);
  const [envio] = (await chamadas(request, "POST")).filter((c) => c.caminho === "/phone-e2e/messages");
  expect(envio.corpo).toMatchObject({ type: "template", template: { name: "boas_vindas_e2e" } });
});
