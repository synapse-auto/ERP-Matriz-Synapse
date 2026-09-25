import { expect, test, type Page } from "@playwright/test";

/**
 * E211 — card de contato compartilhado: aparência em desktop e celular e o fluxo real de "Abrir
 * conversa" contra o backend (busca autorizada por telefone).
 *
 * Pré-requisito: banco dev semeado + `e2e/fixtures/cabecalho-conversa.sql` +
 * `e2e/fixtures/contato-compartilhado.sql`. Screenshots em `PLAYWRIGHT_CAPTURAS`.
 */
const LEAD_ID = "e2100000-0000-4000-8000-000000000001";
const ATENDIMENTO_ID = "e2100000-0000-4000-8000-0000000000a1";
const CAPTURAS = process.env.PLAYWRIGHT_CAPTURAS ?? "test-results/contato-compartilhado";

async function entrarComoAna(page: Page) {
  await page.goto("/login");
  await page.getByLabel(/e-?mail/i).fill("ana@dev.local");
  await page.locator('input[type="password"]').fill("atendente123");
  await page.getByRole("button", { name: /entrar/i }).click();
  // Primeira compilação do `next dev` pode demorar a redirecionar depois do login.
  await expect(page).toHaveURL(/\/atendimentos/, { timeout: 30_000 });
}

async function abrirConversaComContato(page: Page) {
  await page.goto(`/atendimentos?leadId=${LEAD_ID}&atendimentoId=${ATENDIMENTO_ID}&visao=ATIVOS`);
  const card = page.locator('[data-slot="cartao-contato"]').first();
  await expect(card).toBeVisible();
  await card.scrollIntoViewIfNeeded();
  return card;
}

for (const { nome, largura, altura } of [
  { nome: "desktop", largura: 1280, altura: 800 },
  { nome: "celular", largura: 390, altura: 844 },
]) {
  test(`card de contato compartilhado — ${nome}: nada transborda e cada número tem suas ações`, async ({ page }) => {
    await page.setViewportSize({ width: largura, height: altura });
    await entrarComoAna(page);
    await abrirConversaComContato(page);

    const cards = page.locator('[data-slot="cartao-contato"]');
    await expect(cards).toHaveCount(3);
    const grupo = cards.first().locator("xpath=..");
    await grupo.screenshot({ path: `${CAPTURAS}/card-${nome}.png` });

    for (const card of await cards.all()) {
      expect(await card.evaluate((el) => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1);
    }
    // 3 números válidos → 3 "Abrir conversa"; contato sem telefone não ganha botão.
    await expect(page.getByRole("button", { name: /^Abrir conversa com / })).toHaveCount(3);
    await expect(page.getByRole("link", { name: /^Ligar para / })).toHaveCount(3);
    await expect(page.getByText("Sem telefone no cartão")).toBeVisible();
  });
}

test("abrir conversa de um número já acessível abre o atendimento autorizado", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await entrarComoAna(page);
  await abrirConversaComContato(page);

  await page.getByRole("button", { name: "Abrir conversa com +55 61 98888-7777" }).click();

  await expect(page.locator('[data-slot="cabecalho-conversa"]')).toContainText("Contato já atendido pela Ana");
  await page.screenshot({ path: `${CAPTURAS}/abriu-conversa.png` });
});

test("número sem conversa acessível só oferece novo contato preenchido, sem criar nada", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await entrarComoAna(page);
  await abrirConversaComContato(page);
  const pedidosDeCriacao: string[] = [];
  page.on("request", (pedido) => {
    if (pedido.method() === "POST" && pedido.url().includes("/api/v1/atendimentos/novo-contato")) {
      pedidosDeCriacao.push(pedido.url());
    }
  });

  await page.getByRole("button", { name: "Abrir conversa com +55 61 97777-1234" }).click();
  await expect(page.getByText("Nenhuma conversa acessível com este número.")).toBeVisible();
  await page.screenshot({ path: `${CAPTURAS}/sem-conversa.png` });

  await page.getByRole("button", { name: "Iniciar novo contato com +55 61 97777-1234" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo).toBeVisible();
  await expect(dialogo.getByLabel("Nome do contato")).toHaveValue(
    "Fornecedora Vidros Planalto Central Comércio e Representações Ltda",
  );
  await expect(dialogo.getByLabel("Telefone")).toHaveValue("(61) 97777-1234");
  await page.screenshot({ path: `${CAPTURAS}/novo-contato-preenchido.png` });

  await page.keyboard.press("Escape");
  await expect(dialogo).toBeHidden();
  expect(pedidosDeCriacao).toEqual([]);
});
