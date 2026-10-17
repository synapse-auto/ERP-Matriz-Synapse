import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";

import { expect, test, type Browser, type Locator, type Page } from "@playwright/test";

/**
 * Convite colaborativo no navegador real (docs/51): backend e PostgreSQL reais, perfil dev.
 *
 * Pré-requisito: `PSQL` executa SQL no banco do backend como dono, por exemplo
 *   PSQL="docker exec -i synapse-postgres psql -U synapse -d <banco> -At"
 * A fixture `fixtures/convite-colaborativo.sql` é reaplicada antes de cada teste. As capturas vão para
 * `PLAYWRIGHT_CAPTURAS` (padrão `test-results/convite-colaborativo`) antes das asserções.
 */
const LEAD_ID = "c0c00000-0000-4000-8000-000000000001";
const ATENDIMENTO_ID = "c0c00000-0000-4000-8000-0000000000a1";
const NOME_LONGO = "Maria Aparecida dos Santos Vasconcelos Albuquerque de Oliveira";
const CAPTURAS = process.env.PLAYWRIGHT_CAPTURAS ?? "test-results/convite-colaborativo";
const PSQL = process.env.PSQL ?? "";
const FIXTURE = path.join(__dirname, "fixtures", "convite-colaborativo.sql");

test.describe.configure({ mode: "serial" });
test.setTimeout(90_000);
test.skip(!PSQL, "defina PSQL para reaplicar a fixture no banco do backend");

function sql(consulta: string) {
  return execSync(PSQL, { input: consulta, encoding: "utf8" }).trim();
}

function responsaveis() {
  return sql(
    `SELECT l.atendente_responsavel_id || '|' || a.atendente_id FROM lead l JOIN atendimento a ON a.lead_id = l.id WHERE a.id = '${ATENDIMENTO_ID}';`,
  );
}

async function entrar(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel(/e-?mail/i).fill(email);
  await page.locator('input[type="password"]').fill("atendente123");
  await page.getByRole("button", { name: /entrar/i }).click();
  await expect(page).toHaveURL(/\/atendimentos/, { timeout: 30_000 });
  await page.evaluate(() => document.fonts.ready);
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.body).fontFamily)).toContain("Inter");
}

async function abrirConversa(page: Page, visao: string) {
  await page.goto(`/atendimentos?leadId=${LEAD_ID}&atendimentoId=${ATENDIMENTO_ID}&visao=${visao}`);
  const cabecalho = page.locator('[data-slot="cabecalho-conversa"]');
  await expect(cabecalho).toContainText("Cliente Convite", { timeout: 30_000 });
  return cabecalho;
}

/** Ação do cabeçalho que pode estar na barra ou no "Mais ações", conforme a largura. */
async function acionar(page: Page, cabecalho: Locator, nome: RegExp) {
  const direto = cabecalho.getByRole("button", { name: nome });
  if (await direto.first().isVisible().catch(() => false)) {
    await direto.first().click();
    return;
  }
  await cabecalho.getByRole("button", { name: "Mais ações" }).click();
  await page.getByRole("menuitem", { name: nome }).click();
}

/** A conversa aberta, e não a prévia da lista lateral, que também mostra a última mensagem. */
function historico(page: Page) {
  return page.locator('[data-slot="historico-mensagens"]');
}

function registrarPosts(page: Page) {
  const posts: string[] = [];
  page.on("request", (requisicao) => {
    if (requisicao.method() === "POST") posts.push(new URL(requisicao.url()).pathname);
  });
  return posts;
}

async function novaSessao(browser: Browser, email: string, largura = 1366) {
  const contexto = await browser.newContext({ viewport: { width: largura, height: 820 } });
  const page = await contexto.newPage();
  await entrar(page, email);
  return page;
}

test.beforeEach(() => {
  sql(readFileSync(FIXTURE, "utf8"));
});

for (const largura of [1366, 390] as const) {
  test(`modal de convite ${largura}px: seleciona, confirma e só chama /convidar`, async ({ browser }) => {
    const anaId = sql("SELECT id FROM usuario WHERE email = 'ana@dev.local';");
    const mariaId = sql("SELECT id FROM usuario WHERE email = 'maria.convite@dev.local';");
    const page = await novaSessao(browser, "ana@dev.local", largura);
    const posts = registrarPosts(page);
    const cabecalho = await abrirConversa(page, "ATIVOS");

    await acionar(page, cabecalho, /Convidar/);
    const dialogo = page.getByRole("dialog");
    await expect(dialogo).toBeVisible();
    await expect(dialogo.getByTestId("convite-equipe-atual")).toContainText("Ana Atendente");

    const candidato = dialogo.getByRole("button", { name: NOME_LONGO });
    await candidato.click();
    await expect(candidato).toHaveAttribute("aria-pressed", "true");
    await page.screenshot({ path: `${CAPTURAS}/modal-${largura}.png` });

    // Nada transborda: nem o modal, nem o botão do nome longo.
    expect(await dialogo.evaluate((el) => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1);
    expect(await candidato.evaluate((el) => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1);
    expect(posts.filter((url) => url.includes("/convidar"))).toHaveLength(0);

    if (largura === 390) {
      await page.setViewportSize({ width: 390, height: 320 });
      // Resize/entrada do Base UI podem estar em transição: esperar a geometria, não um sleep.
      await expect.poll(async () => (await dialogo.boundingBox())!.y).toBeGreaterThanOrEqual(0);
      await expect.poll(async () => {
        const limites = (await dialogo.boundingBox())!;
        return limites.y + limites.height;
      }).toBeLessThanOrEqual(320);
      await candidato.focus();
      await page.keyboard.press("Tab");
      await page.keyboard.press("Tab");
      await expect(dialogo.getByRole("button", { name: "Enviar convite" })).toBeFocused();
      await dialogo.getByRole("button", { name: "Enviar convite" }).scrollIntoViewIfNeeded();
      await expect(dialogo.getByRole("button", { name: "Enviar convite" })).toBeInViewport({ ratio: 1 });
      await page.screenshot({ path: `${CAPTURAS}/modal-390x320.png` });
    }

    await dialogo.getByRole("button", { name: "Enviar convite" }).click();
    await expect(dialogo).toBeHidden();

    expect(posts).toContain(`/api/v1/atendimentos/${ATENDIMENTO_ID}/convidar`);
    expect(posts.some((url) => url.includes("/transferir"))).toBe(false);
    expect(responsaveis()).toBe(`${anaId}|${anaId}`);
    expect(sql(`SELECT status FROM pedido_entrada_atendimento WHERE atendimento_id = '${ATENDIMENTO_ID}' AND solicitante_id = '${mariaId}';`))
      .toBe("PENDENTE");
    await page.context().close();
  });
}

test("A convida, B aceita e os dois conversam em tempo real sem trocar o responsável", async ({ browser }) => {
  const anaId = sql("SELECT id FROM usuario WHERE email = 'ana@dev.local';");
  const ana = await novaSessao(browser, "ana@dev.local");
  const bruno = await novaSessao(browser, "bruno@dev.local");

  const cabecalhoAna = await abrirConversa(ana, "ATIVOS");
  await acionar(ana, cabecalhoAna, /Convidar/);
  const dialogo = ana.getByRole("dialog");
  await dialogo.getByRole("button", { name: "Bruno Atendente" }).click();
  await dialogo.getByRole("button", { name: "Enviar convite" }).click();
  await expect(dialogo).toBeHidden();
  expect(responsaveis()).toBe(`${anaId}|${anaId}`);

  const cabecalhoBruno = await abrirConversa(bruno, "PENDENTES");
  await expect(bruno.getByPlaceholder("Digite uma mensagem...")).toHaveCount(0);
  await expect(cabecalhoBruno).toContainText("Convite para participar");
  await acionar(bruno, cabecalhoBruno, /Aceitar convite/);
  await expect(cabecalhoBruno).toContainText(/suas mensagens não transferem/);
  expect(responsaveis()).toBe(`${anaId}|${anaId}`);

  // Sem F5: o aceite libera o estado canônico; a ficha também precisa estar acessível.
  await expect(bruno.getByText("Detalhes do lead", { exact: true })).toBeVisible();
  await expect(bruno.getByText("Lead não encontrado", { exact: false })).toHaveCount(0);

  await bruno.getByPlaceholder("Digite uma mensagem...").fill("B: posso ajudar com as medidas.");
  await bruno.getByRole("button", { name: "Enviar", exact: true }).click();
  await expect(historico(ana).getByText("B: posso ajudar com as medidas.")).toBeVisible({ timeout: 10_000 });
  // Autoria: a resposta do convidado não pode parecer da responsável.
  await expect(historico(ana).getByText("Bruno Atendente")).toBeVisible();

  await ana.getByPlaceholder("Digite uma mensagem...").fill("A: obrigada, sigo com o orçamento.");
  await ana.getByRole("button", { name: "Enviar", exact: true }).click();
  await expect(historico(bruno).getByText("A: obrigada, sigo com o orçamento.")).toBeVisible({ timeout: 10_000 });

  await bruno.reload();
  await expect(historico(bruno).getByText("A: obrigada, sigo com o orçamento.")).toBeVisible();
  await expect(bruno.getByPlaceholder("Digite uma mensagem...")).toBeEnabled();
  await expect(cabecalhoBruno).toContainText(/suas mensagens não transferem/);
  await expect(bruno.getByText("Não foi possível", { exact: false })).toHaveCount(0);

  await ana.screenshot({ path: `${CAPTURAS}/duas-sessoes-ana.png` });
  await bruno.screenshot({ path: `${CAPTURAS}/duas-sessoes-bruno.png` });

  expect(responsaveis()).toBe(`${anaId}|${anaId}`);
  await expect(cabecalhoAna).toContainText("Ana Atendente");
  await expect(cabecalhoBruno).toContainText("Ana Atendente");
  await ana.context().close();
  await bruno.context().close();
});
