import { expect, test, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { prepararFrontendV2, cartoesFixture as cartoes, temaFixture as tema } from "./fixtures/frontend-v2";

function preparar(page: Page, habilitado: boolean, quantidade = 15, temaDaFixture = tema) {
  return prepararFrontendV2(page, { features: habilitado ? ["atendimentos_layout_v2"] : [], quantidade, tema: temaDaFixture });
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
