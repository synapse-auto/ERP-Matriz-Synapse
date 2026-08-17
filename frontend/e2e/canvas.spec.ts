import { expect, test } from "@playwright/test";

const email = process.env.PLAYWRIGHT_EMAIL ?? "admin@dev.local";
const senha = process.env.PLAYWRIGHT_SENHA ?? "admin123";

async function entrar(page: import("@playwright/test").Page) {
  await page.goto("/login");
  await page.getByLabel(/e-?mail/i).fill(email);
  await page.getByLabel(/senha/i).fill(senha);
  await page.getByRole("button", { name: /entrar/i }).click();
  await expect(page).toHaveURL(/\/atendimentos/);
}

test("a lista rola por dentro sem mover a sidebar nem o cabeçalho", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await entrar(page);

  const lista = page.locator(
    '[data-slot="lista-conversas-scroll"] [data-slot="scroll-area-viewport"]',
  );
  await expect(lista).toBeVisible();
  await lista.locator("button").first().click();

  const sidebar = page.locator('[data-slot="sidebar"]');
  const cabecalho = page.locator('[data-slot="cabecalho-conversa"]');
  await expect(sidebar).toBeVisible();
  await expect(cabecalho).toBeVisible();

  const antes = {
    sidebar: await sidebar.boundingBox(),
    cabecalho: await cabecalho.boundingBox(),
  };

  await lista.evaluate((elemento) => {
    const preenchimento = document.createElement("div");
    preenchimento.dataset.playwright = "lista-alta";
    preenchimento.style.height = "2400px";
    preenchimento.style.flex = "0 0 2400px";
    elemento.append(preenchimento);
    elemento.scrollTop = elemento.scrollHeight;
  });

  await expect.poll(() => lista.evaluate((elemento) => elemento.scrollTop)).toBeGreaterThan(0);
  expect(await page.evaluate(() => document.scrollingElement?.scrollTop)).toBe(0);
  expect(await sidebar.boundingBox()).toEqual(antes.sidebar);
  expect(await cabecalho.boundingBox()).toEqual(antes.cabecalho);
});

test("o login permanece inteiramente acessível em viewport baixa", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 720 });
  await page.goto("/login");

  const formulario = page.locator("form");
  await expect(formulario).toBeVisible();
  const caixa = await formulario.boundingBox();

  expect(caixa).not.toBeNull();
  expect(caixa!.y).toBeGreaterThanOrEqual(0);
  expect(caixa!.y + caixa!.height).toBeLessThanOrEqual(720);
  expect(await page.evaluate(() => document.scrollingElement?.scrollTop)).toBe(0);
});
