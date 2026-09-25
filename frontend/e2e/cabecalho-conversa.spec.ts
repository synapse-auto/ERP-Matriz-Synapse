import { expect, test, type Locator, type Page } from "@playwright/test";

/**
 * E210 — validação visual do cabeçalho da conversa com transbordo para o "⋯".
 *
 * Pré-requisito: banco de desenvolvimento semeado (perfil dev) + `e2e/fixtures/cabecalho-conversa.sql`
 * (lead de nome longo, telefone, cinco tags, atendido pela Ana). Os screenshots vão para
 * `PLAYWRIGHT_CAPTURAS` (padrão `test-results/cabecalho-conversa`) antes das asserções, para que
 * a mesma rodada sirva de "antes" contra um código antigo.
 */
const LEAD_ID = "e2100000-0000-4000-8000-000000000001";
const ATENDIMENTO_ID = "e2100000-0000-4000-8000-0000000000a1";
const NOME_DO_LEAD = "Maria Aparecida dos Santos Vasconcelos Albuquerque de Oliveira";
const CAPTURAS = process.env.PLAYWRIGHT_CAPTURAS ?? "test-results/cabecalho-conversa";

const PAPEIS = [
  { papel: "gestor", email: "gestor@dev.local", senha: "gestor123", visao: "TODOS" },
  { papel: "atendente", email: "ana@dev.local", senha: "atendente123", visao: "ATIVOS" },
] as const;
const LARGURAS = [1024, 1280, 1366, 1920] as const;

/** Toda ação do cabeçalho precisa estar na barra ou no "⋯" — nunca inacessível. */
const ACOES_ESPERADAS = [/Convidar/, /Transferir/, /Finalizar/, /Buscar na conversa/, /Tags/, /Telefone/];

async function entrar(page: Page, email: string, senha: string) {
  await page.goto("/login");
  await page.getByLabel(/e-?mail/i).fill(email);
  await page.locator("input[type=\"password\"]").fill(senha);
  await page.getByRole("button", { name: /entrar/i }).click();
  await expect(page).toHaveURL(/\/atendimentos/);
}

async function abrirConversa(page: Page, visao: string) {
  await page.goto(`/atendimentos?leadId=${LEAD_ID}&atendimentoId=${ATENDIMENTO_ID}&visao=${visao}`);
  const cabecalho = page.locator('[data-slot="cabecalho-conversa"]');
  await expect(cabecalho).toContainText(NOME_DO_LEAD.slice(0, 12));
  return cabecalho;
}

async function acessivelNaBarraOuNoMenu(page: Page, barra: Locator, nome: RegExp): Promise<boolean> {
  const naBarra = barra.getByRole("button", { name: nome }).or(barra.getByRole("link", { name: nome }));
  if (await naBarra.first().isVisible().catch(() => false)) return true;
  const menu = barra.getByRole("button", { name: "Mais ações" });
  if (!(await menu.isVisible().catch(() => false))) return false;
  await menu.click();
  await expect(page.getByRole("menu")).toBeVisible();
  const item = page.getByRole("menuitem", { name: nome }).or(page.getByRole("menuitemcheckbox", { name: nome }));
  const achou = await item.first().waitFor({ state: "visible", timeout: 2_000 }).then(() => true, () => false);
  await page.keyboard.press("Escape");
  await expect(page.getByRole("menu")).toBeHidden();
  return achou;
}

for (const { papel, email, senha, visao } of PAPEIS) {
  for (const largura of LARGURAS) {
    for (const painel of ["painel-aberto", "painel-retraido"] as const) {
      test(`cabeçalho ${papel} ${largura}px ${painel}: nada cortado e todas as ações acessíveis`, async ({ page }) => {
        await page.setViewportSize({ width: largura, height: 800 });
        await entrar(page, email, senha);
        const cabecalho = await abrirConversa(page, visao);

        const retrair = page.getByRole("button", { name: "Retrair detalhes do lead" });
        if (painel === "painel-retraido" && await retrair.isVisible().catch(() => false)) {
          await retrair.click();
        }
        if (painel === "painel-aberto") {
          const reabrir = page.getByRole("button", { name: "Reabrir detalhes do lead" });
          if (await reabrir.isVisible().catch(() => false)) await reabrir.click();
        }
        await page.waitForTimeout(300);
        await page.screenshot({ path: `${CAPTURAS}/${papel}-${largura}-${painel}.png` });
        await cabecalho.screenshot({ path: `${CAPTURAS}/${papel}-${largura}-${painel}-cabecalho.png` });

        // Nada transborda do cabeçalho e nenhum controle visível fica cortado.
        const caixa = (await cabecalho.boundingBox())!;
        expect(await cabecalho.evaluate((el) => el.scrollWidth - el.clientWidth)).toBeLessThanOrEqual(1);
        const barra = cabecalho.locator('[data-slot="acoes-cabecalho"]');
        const controles = barra.locator(":scope > div button, :scope > div a, :scope > button");
        for (const controle of await controles.all()) {
          if (!(await controle.isVisible())) continue;
          const c = (await controle.boundingBox())!;
          expect(c.x).toBeGreaterThanOrEqual(caixa.x - 1);
          expect(c.x + c.width).toBeLessThanOrEqual(caixa.x + caixa.width + 1);
        }

        // Toda ação existe na barra ou no "⋯".
        for (const nome of ACOES_ESPERADAS) {
          expect(await acessivelNaBarraOuNoMenu(page, barra, nome), String(nome)).toBe(true);
        }

        // A área de mensagens começa logo abaixo do cabeçalho e continua com altura útil.
        const mensagens = (await page.locator('[data-slot="lista-mensagens"]').boundingBox())!;
        expect(mensagens.y).toBeGreaterThanOrEqual(caixa.y + caixa.height - 1);
        expect(mensagens.height).toBeGreaterThan(300);
      });
    }
  }
}
