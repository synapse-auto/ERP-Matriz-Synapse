import { execSync } from "node:child_process";

import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

/**
 * Gestão (docs/47) em navegador real contra backend real.
 *
 * Pré-requisitos: backend no perfil dev com o seed aplicado, frontend apontando para ele e
 * `e2e/fixtures/gestao.sql` aplicado. `PLAYWRIGHT_RESET_GESTAO` recebe o comando que reaplica a
 * fixture antes de cada teste (ex.: `docker exec -i e2e-postgres psql -U synapse -d synapse_crm`),
 * para os testes não dependerem da ordem. Capturas vão para `PLAYWRIGHT_CAPTURAS`.
 */
const API = process.env.PLAYWRIGHT_API_URL ?? "http://localhost:8080";
const CAPTURAS = process.env.PLAYWRIGHT_CAPTURAS ?? "test-results/gestao";
const RESET = process.env.PLAYWRIGHT_RESET_GESTAO;

const GESTOR = { email: "gestor@dev.local", senha: "gestor123" };
const SUBGESTOR = { email: "subgestor@dev.local", senha: "subgestor123" };
const ANA = { email: "ana@dev.local", senha: "atendente123" };

test.beforeEach(() => {
  if (RESET) execSync(`${RESET} < e2e/fixtures/gestao.sql`, { stdio: "ignore" });
});

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

async function salvarPerfilViaApi(request: APIRequestContext, token: string, papel: string, acoes: Record<string, boolean>) {
  const perfis = await (await request.get(`${API}/api/v1/gestao/permissoes/perfis`, { headers: { Authorization: `Bearer ${token}` } })).json();
  const revisao = perfis.find((p: { papel: string }) => p.papel === papel).revisao;
  const resposta = await request.put(`${API}/api/v1/gestao/permissoes/perfis/${papel}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { revisaoEsperada: revisao, niveis: {}, acoes },
  });
  expect(resposta.status()).toBe(200);
}

async function semRolagemHorizontal(page: Page) {
  const larguras = await page.evaluate(() => ({ documento: document.documentElement.scrollWidth, janela: window.innerWidth }));
  expect(larguras.documento).toBeLessThanOrEqual(larguras.janela + 1);
}

const LARGURAS = [1920, 1366, 1280, 390] as const;

for (const largura of LARGURAS) {
  test(`capturas das três abas em ${largura}px, sem rolagem horizontal da página`, async ({ page }) => {
    await page.setViewportSize({ width: largura, height: largura === 390 ? 844 : 920 });
    await entrar(page, GESTOR);
    await page.goto("/gestao");
    await expect(page.getByRole("heading", { name: "Gestão" })).toBeVisible();
    await expect(page.getByText("carla@dev.local")).toBeVisible();
    await semRolagemHorizontal(page);
    await page.screenshot({ path: `${CAPTURAS}/01-equipe-${largura}.png`, fullPage: true });

    if (largura !== 390) {
      await page.getByRole("button", { name: "Novo usuário" }).click();
      await expect(page.getByRole("dialog")).toBeVisible();
      await page.screenshot({ path: `${CAPTURAS}/02-novo-usuario-${largura}.png` });
      await page.keyboard.press("Escape");
      await expect(page.getByRole("dialog")).toBeHidden();
    }

    await page.getByRole("tab", { name: /Permissões/ }).click();
    await expect(page.getByRole("radio", { name: /Subgestor/ })).toBeVisible();
    await semRolagemHorizontal(page);
    await page.screenshot({ path: `${CAPTURAS}/03-permissoes-subgestor-${largura}.png`, fullPage: largura === 390 });

    await page.getByRole("tab", { name: /Exceções/ }).click();
    await page.getByRole("button", { name: /Ana Atendente/ }).click();
    await expect(page.getByRole("region", { name: "Ana Atendente" }).or(page.getByLabel("Ana Atendente"))).toBeVisible();
    await semRolagemHorizontal(page);
    await page.screenshot({ path: `${CAPTURAS}/04-excecoes-usuario-${largura}.png`, fullPage: largura === 390 });
  });
}

test("Equipe: modal de novo usuário exige senha, oferece só Subgestor/Atendente e cria com IA fora do rodízio", async ({ page }) => {
  await entrar(page, GESTOR);
  await page.goto("/gestao");
  await page.getByRole("button", { name: "Novo usuário" }).click();
  const dialogo = page.getByRole("dialog");
  await expect(dialogo.getByRole("radio")).toHaveCount(2);
  await expect(dialogo.getByRole("radio", { name: /Gestor/ })).toHaveCount(0);
  const sufixo = Date.now().toString(36);
  await dialogo.getByPlaceholder("Nome completo").fill(`Nova Pessoa ${sufixo}`);
  await dialogo.getByPlaceholder("nome@empresa.com.br").fill(`nova-${sufixo}@dev.local`);
  await dialogo.locator('input[type="password"]').fill("senha-e2e-segura");
  await dialogo.getByRole("button", { name: "Salvar usuário" }).click();
  await expect(dialogo).toBeHidden();
  const linha = page.getByRole("row", { name: new RegExp(`Nova Pessoa ${sufixo}`) });
  await expect(linha).toBeVisible();
  await expect(linha.getByText("Fora do rodízio")).toBeVisible();
});

test("Permissões: busca, rascunho com descartar, salvar e alteração sensível confirmada", async ({ page }) => {
  await entrar(page, GESTOR);
  await page.goto("/gestao?aba=permissoes&perfil=ATENDENTE");
  await page.getByPlaceholder(/Buscar permissão/).fill("resumo");
  await expect(page.getByRole("heading", { name: "Resumo por IA" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Tags" })).toHaveCount(0);
  await page.getByPlaceholder(/Buscar permissão/).fill("xyzsemresultado");
  await expect(page.getByText("Nenhum módulo corresponde à busca.")).toBeVisible();
  await page.getByPlaceholder(/Buscar permissão/).fill("");

  const gerar = page.getByRole("switch", { name: "Gerar resumo" });
  await expect(gerar).toBeChecked();
  await gerar.click();
  await expect(page.getByText("1 alteração não salva")).toBeVisible();
  await page.getByRole("button", { name: "Descartar" }).click();
  await expect(gerar).toBeChecked();

  // nível do módulo como preset: "Ver" desliga "Gerar resumo" (nível mínimo Editar) e explica
  const nivel = page.getByRole("radiogroup", { name: "Nível de Resumo por IA" });
  await nivel.getByRole("radio", { name: "Ver" }).click();
  await expect(gerar).not.toBeChecked();
  await expect(gerar).toBeDisabled();
  await expect(page.getByText("Liberado ao subir o nível do módulo para Editar").first()).toBeVisible();
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(page.getByText(/alterações? não salvas?/)).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole("switch", { name: "Gerar resumo" })).not.toBeChecked();

  // sensível: finalizar em lote pede confirmação
  await page.getByRole("switch", { name: "Finalizar atendimentos em lote" }).click();
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(page.getByRole("dialog", { name: "Confirmar alteração sensível" })).toBeVisible();
  await page.getByRole("button", { name: "Salvar mesmo assim" }).click();
  await expect(page.getByText(/alterações? não salvas?/)).toHaveCount(0);
  await page.screenshot({ path: `${CAPTURAS}/permissoes-atendente-salvo.png`, fullPage: true });
});

test("Permissões: conflito com outro gestor não sobrescreve e mantém o rascunho", async ({ page, request }) => {
  await entrar(page, GESTOR);
  await page.goto("/gestao?aba=permissoes&perfil=ATENDENTE");
  await page.getByRole("switch", { name: "Criar lembrete" }).click();
  await salvarPerfilViaApi(request, await tokenDe(request, { email: "admin@dev.local", senha: "admin123" }), "ATENDENTE", { "tags.aplicar": false });
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(page.getByRole("alert").filter({ hasText: /Outra pessoa salvou/ })).toBeVisible();
  await expect(page.getByText("1 alteração não salva")).toBeVisible();
  await page.screenshot({ path: `${CAPTURAS}/conflito.png` });
  await page.getByRole("button", { name: "Recarregar versão atual" }).click();
  await expect(page.getByRole("switch", { name: "Aplicar e remover tags nos leads" })).not.toBeChecked();
});

test("Exceções: personalizar, restaurar por linha, voltar ao padrão e copiar com prévia", async ({ page }) => {
  await entrar(page, GESTOR);
  await page.goto("/gestao?aba=excecoes");
  await page.getByRole("button", { name: /Ana Atendente/ }).click();
  const tags = page.getByRole("switch", { name: "Aplicar e remover tags nos leads" });
  await tags.click();
  await page.getByRole("switch", { name: "Gerar resumo" }).click();
  await expect(page.getByText("PERSONALIZADO").first()).toBeVisible();
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(page.getByRole("button", { name: /Ana Atendente/ })).toContainText("2 exceções");
  await page.screenshot({ path: `${CAPTURAS}/06-excecoes-personalizadas.png`, fullPage: true });

  await page.getByRole("button", { name: "Restaurar o padrão de Aplicar e remover tags nos leads" }).click();
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(page.getByRole("button", { name: /Ana Atendente/ })).toContainText("1 exceção");

  // copiar de GESTOR aparece bloqueado; copiar de outra atendente abre prévia calculada no servidor
  await page.getByRole("combobox", { name: "Copiar de outro usuário…" }).click();
  await expect(page.getByRole("option", { name: /Gestor.*acesso fixo/ })).toHaveAttribute("aria-disabled", "true");
  await page.screenshot({ path: `${CAPTURAS}/05-copiar-permissoes.png` });
  await page.getByRole("option", { name: /Carla Atendente/ }).click();
  const previa = page.getByRole("dialog", { name: "Copiar permissões" });
  await expect(previa.getByText("O que muda")).toBeVisible();
  await previa.getByRole("button", { name: "Aplicar ao rascunho" }).click();
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(page.getByRole("button", { name: /Ana Atendente/ })).toContainText(/padrão do perfil/);

  await page.getByRole("switch", { name: "Gerar resumo" }).click();
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await page.getByRole("button", { name: "Voltar ao padrão do perfil" }).click();
  await expect(page.getByText("Remove todas as exceções de Ana Atendente.")).toBeVisible();
  await page.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(page.getByRole("button", { name: /Ana Atendente/ })).toContainText(/padrão do perfil/);
});

test("rascunho protegido ao trocar de contexto e teclado nos níveis", async ({ page }) => {
  await entrar(page, GESTOR);
  await page.goto("/gestao?aba=permissoes&perfil=SUBGESTOR");
  const nivel = page.getByRole("radiogroup", { name: "Nível de Tags" });
  await nivel.getByRole("radio", { checked: true }).focus();
  await page.keyboard.press("ArrowLeft");
  await expect(nivel.getByRole("radio", { name: "Editar" })).toBeChecked();
  await page.getByRole("tab", { name: /Equipe/ }).click();
  await expect(page.getByRole("dialog", { name: "Descartar alterações?" })).toBeVisible();
  await page.getByRole("button", { name: "Continuar editando" }).click();
  await expect(nivel.getByRole("radio", { name: "Editar" })).toBeChecked();
  await page.getByRole("tab", { name: /Equipe/ }).click();
  await page.getByRole("button", { name: "Descartar e continuar" }).click();
  await expect(page).toHaveURL(/\/gestao$/);
});

test("estado de erro: falha ao carregar perfis mostra erro com tentar novamente", async ({ page }) => {
  await entrar(page, GESTOR);
  await page.route("**/api/v1/gestao/permissoes/perfis", (rota) => rota.fulfill({ status: 500, body: "{}" }));
  await page.goto("/gestao?aba=permissoes");
  await expect(page.getByText("Não foi possível carregar a Gestão.")).toBeVisible();
});

test("SUBGESTOR sem delegação: lê Gestão sem controles de edição; ATENDENTE não vê Gestão", async ({ page, browser }) => {
  await entrar(page, SUBGESTOR);
  await expect(page.getByRole("link", { name: "Gestão" })).toBeVisible();
  await page.goto("/gestao");
  await expect(page.getByText("Somente leitura")).toBeVisible();
  await expect(page.getByRole("button", { name: "Novo usuário" })).toHaveCount(0);
  await page.getByRole("tab", { name: /Permissões/ }).click();
  await expect(page.getByText("Suas permissões são definidas por Gestor ou Administrador.")).toBeVisible();
  await expect(page.getByRole("switch").first()).toBeDisabled();

  const outra = await browser.newPage();
  await entrar(outra, ANA);
  await expect(outra.getByRole("link", { name: "Gestão" })).toHaveCount(0);
  await outra.goto("/gestao");
  await expect(outra.getByText("Você não tem acesso à Gestão.")).toBeVisible();
  await outra.close();
});

test("revogação reflete sem F5: menu da atendente perde Mensagens Rápidas quando o gestor revoga", async ({ page, request }) => {
  await entrar(page, ANA);
  const item = page.getByRole("link", { name: "Mensagens Rápidas" });
  await expect(item).toBeVisible();
  await salvarPerfilViaApi(request, await tokenDe(request, GESTOR), "ATENDENTE", { "mensagens_rapidas.usar": false });
  await expect(item).toHaveCount(0, { timeout: 15_000 });
  // URL direta cai na guarda de rota; o backend continua recusando a consulta (ver permissoes-na-interface.spec.ts).
  await page.goto("/mensagens-rapidas");
  await expect(page.getByRole("heading", { name: "Sem acesso a esta área" })).toBeVisible();
});
