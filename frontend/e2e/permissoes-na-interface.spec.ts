import { execSync } from "node:child_process";

import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

/**
 * Permissões efetivas na interface (Gestão, docs/47): o que a gestão revoga some da tela sem F5 e
 * continua negado no backend. Mesmos pré-requisitos de gestao.spec.ts (backend dev + fixture).
 */
const API = process.env.PLAYWRIGHT_API_URL ?? "http://localhost:8080";
const RESET = process.env.PLAYWRIGHT_RESET_GESTAO;
const ESPERA_DO_AVISO = { timeout: 15_000 };

const GESTOR = { email: "gestor@dev.local", senha: "gestor123" };
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

function autorizacao(token: string) {
  return { Authorization: `Bearer ${token}` };
}

async function salvarPerfil(request: APIRequestContext, token: string, acoes: Record<string, boolean>) {
  const perfis = await (await request.get(`${API}/api/v1/gestao/permissoes/perfis`, { headers: autorizacao(token) })).json();
  const revisao = perfis.find((p: { papel: string }) => p.papel === "ATENDENTE").revisao;
  const resposta = await request.put(`${API}/api/v1/gestao/permissoes/perfis/ATENDENTE`, {
    headers: autorizacao(token),
    data: { revisaoEsperada: revisao, niveis: {}, acoes },
  });
  expect(resposta.status()).toBe(200);
}

async function idDe(request: APIRequestContext, token: string, email: string): Promise<string> {
  const usuarios = await (await request.get(`${API}/api/v1/usuarios`, { headers: autorizacao(token) })).json();
  return usuarios.find((u: { email: string }) => u.email === email).id;
}

async function salvarExcecoes(request: APIRequestContext, token: string, usuarioId: string, acoes: Record<string, boolean>) {
  const detalhe = await (await request.get(`${API}/api/v1/gestao/permissoes/usuarios/${usuarioId}`, { headers: autorizacao(token) })).json();
  const resposta = await request.put(`${API}/api/v1/gestao/permissoes/usuarios/${usuarioId}/excecoes`, {
    headers: autorizacao(token),
    data: { revisaoEsperada: detalhe.revisao, niveis: {}, acoes },
  });
  expect(resposta.status()).toBe(200);
}

async function restaurarPadrao(request: APIRequestContext, token: string, usuarioId: string) {
  const detalhe = await (await request.get(`${API}/api/v1/gestao/permissoes/usuarios/${usuarioId}`, { headers: autorizacao(token) })).json();
  const resposta = await request.delete(
    `${API}/api/v1/gestao/permissoes/usuarios/${usuarioId}/excecoes?revisaoEsperada=${detalhe.revisao}`,
    { headers: autorizacao(token) },
  );
  expect(resposta.status()).toBe(200);
}

async function criarTemplateDireto(request: APIRequestContext, token: string) {
  const resposta = await request.post(`${API}/api/v1/whatsapp/templates`, {
    headers: autorizacao(token),
    data: { nome: "retorno_orcamento", idioma: "pt_BR", categoria: "UTILIDADE", corpo: "Ola {{1}}" },
  });
  return resposta.status();
}

test("perfil revoga Criar template: o botão some sem F5, o POST direto com a sessão anterior é negado e a restauração o traz de volta", async ({ page, request }) => {
  const tokenAnaAnterior = await tokenDe(request, ANA);
  const tokenGestor = await tokenDe(request, GESTOR);
  await entrar(page, ANA);
  await page.goto("/templates-whatsapp");
  const novo = page.getByRole("button", { name: "Novo template" });
  await expect(page.getByRole("heading", { name: /Templates/ })).toBeVisible();
  await expect(novo).toBeVisible();

  await salvarPerfil(request, tokenGestor, { "templates.criar": false });
  await expect(novo).toHaveCount(0, ESPERA_DO_AVISO);
  await expect(page.getByRole("heading", { name: /Templates/ })).toBeVisible();
  expect(await criarTemplateDireto(request, tokenAnaAnterior)).toBe(403);

  await salvarPerfil(request, tokenGestor, {});
  await expect(novo).toBeVisible(ESPERA_DO_AVISO);
  expect(await criarTemplateDireto(request, tokenAnaAnterior)).not.toBe(403);
});

test("exceção individual revoga Criar template só para a Ana, sem novo login", async ({ page, request }) => {
  const tokenGestor = await tokenDe(request, GESTOR);
  const ana = await idDe(request, tokenGestor, ANA.email);
  await entrar(page, ANA);
  await page.goto("/templates-whatsapp");
  const novo = page.getByRole("button", { name: "Novo template" });
  await expect(novo).toBeVisible();

  await salvarExcecoes(request, tokenGestor, ana, { "templates.criar": false });
  await expect(novo).toHaveCount(0, ESPERA_DO_AVISO);
  expect(await criarTemplateDireto(request, await tokenDe(request, ANA))).toBe(403);

  await restaurarPadrao(request, tokenGestor, ana);
  await expect(novo).toBeVisible(ESPERA_DO_AVISO);
});

test("duas abas abertas da mesma pessoa perdem o botão juntas", async ({ page, context, request }) => {
  await entrar(page, ANA);
  await page.goto("/templates-whatsapp");
  const outraAba = await context.newPage();
  await outraAba.goto("/templates-whatsapp");
  await expect(page.getByRole("button", { name: "Novo template" })).toBeVisible();
  await expect(outraAba.getByRole("button", { name: "Novo template" })).toBeVisible();

  await salvarPerfil(request, await tokenDe(request, GESTOR), { "templates.criar": false });
  await expect(page.getByRole("button", { name: "Novo template" })).toHaveCount(0, ESPERA_DO_AVISO);
  await expect(outraAba.getByRole("button", { name: "Novo template" })).toHaveCount(0, ESPERA_DO_AVISO);
});

test("rota revogada por URL direta mostra Sem acesso e o menu não oferece o item", async ({ page, request }) => {
  await salvarPerfil(request, await tokenDe(request, GESTOR), { "templates.ver": false, "templates.criar": false });
  await entrar(page, ANA);
  await expect(page.getByRole("link", { name: /Templates/ })).toHaveCount(0);

  await page.goto("/templates-whatsapp");
  await expect(page.getByRole("heading", { name: "Sem acesso a esta área" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Novo template" })).toHaveCount(0);
  await page.getByRole("link", { name: "Voltar para Atendimentos" }).click();
  await expect(page).toHaveURL(/\/atendimentos/);
});

test("Atendimentos sem regressão: a atendente com o padrão continua vendo a lista e o menu operacional", async ({ page }) => {
  await entrar(page, ANA);
  await expect(page.getByRole("heading", { name: "Atendimentos" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Novo atendimento" }).first()).toBeVisible();
  await expect(page.getByRole("link", { name: "Mensagens Rápidas" })).toBeVisible();
  await expect(page.getByRole("link", { name: /Templates/ })).toBeVisible();
});
