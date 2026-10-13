// Evidência visual da D1 da Disbrel (docs/71): telas principais com o tema padrão, o A e o B.
// Não é spec de CI: precisa do backend dev semeado (docker/provisionamento/seed-demonstracao.sql) em
// localhost:8080 e do Next em localhost:3000. O tema é aplicado ANTES, pelo script de provisionamento,
// com as chamadas reais PUT /api/v1/config/marca/*; este script só tira as fotos.
//
//   CAPTURAS=../docs/evidencias/disbrel-d1/padrao node e2e/_capturas-marca-disbrel.mjs
//
// Pré-requisito para a captura da falha de entrega (02): uma mensagem de saída FALHOU na conversa
// "Cliente Teste 2" (ver docs/71, seção de evidência).
import { mkdirSync } from "node:fs";
import { resolve } from "node:path";

import { chromium } from "@playwright/test";

const WEB = process.env.WEB_URL ?? "http://localhost:3000";
const PASTA = resolve(process.env.CAPTURAS ?? "../docs/evidencias/disbrel-d1/padrao");
mkdirSync(PASTA, { recursive: true });

const navegador = await chromium.launch({
  executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined,
});

async function sessao(email, senha, viewport = { width: 1440, height: 900 }) {
  const contexto = await navegador.newContext({ viewport, locale: "pt-BR" });
  const page = await contexto.newPage();
  await page.goto(`${WEB}/login`);
  await page.waitForLoadState("networkidle");
  await page.getByLabel(/e-?mail/i).fill(email);
  await page.locator('input[type="password"]').fill(senha);
  await page.getByRole("button", { name: /entrar/i }).click();
  await page.waitForURL(/\/atendimentos/, { timeout: 60_000 });
  await page.waitForLoadState("networkidle");
  return { contexto, page };
}

async function foto(page, nome) {
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${PASTA}/${nome}.png` });
  console.log("captura:", nome);
}

const LONGE = { x: 900, y: 450 }; // fora da sidebar: ela volta a recolher

// --- Atendente: painel, chat, sidebar, menu do usuário, erro, mobile ---------------------------
{
  const { contexto, page } = await sessao("zelia.demo@dev.local", "atendente123");
  await page.getByRole("tab", { name: /^ativos/i }).click();
  await page.mouse.move(LONGE.x, LONGE.y);
  await page.waitForTimeout(1200);
  await foto(page, "01-painel-de-atendimentos");

  await page.locator('[data-slot="lista-conversas-itens"]').getByText("Cliente Teste 2").first().click();
  await page.mouse.move(LONGE.x, LONGE.y);
  await page.waitForTimeout(2000);
  await foto(page, "02-chat-aberto-com-falha-de-entrega");

  // Sidebar expandida (hover) com um item em hover; o item ativo é "Atendimentos".
  await page.mouse.move(40, 300);
  await page.waitForTimeout(700);
  await page.getByRole("link", { name: /mensagens rápidas/i }).first().hover();
  await foto(page, "03-sidebar-expandida-item-ativo-e-hover");

  // Menu do usuário: presença e a ação de perigo (Sair).
  await page.locator('[data-slot="sidebar"] button').filter({ hasText: /Demon/i }).first().click();
  await foto(page, "04-menu-do-usuario-com-acao-de-perigo");
  await page.keyboard.press("Escape");
  await page.mouse.move(LONGE.x, LONGE.y);

  // Estado de erro: a lista de tags falha (500 interceptado no navegador, só neste contexto).
  await page.route("**/api/v1/tags*", (rota) =>
    rota.request().method() === "GET"
      ? rota.fulfill({ status: 500, contentType: "application/json", body: "{}" })
      : rota.continue(),
  );
  await page.goto(`${WEB}/tags`);
  await page.mouse.move(LONGE.x, LONGE.y);
  await page.waitForTimeout(2500);
  await foto(page, "07-estado-de-erro-de-carregamento");
  await contexto.close();
}

// --- Mobile: navegação inferior ----------------------------------------------------------------
{
  const { contexto, page } = await sessao("zelia.demo@dev.local", "atendente123", { width: 390, height: 844 });
  await page.waitForTimeout(1500);
  await foto(page, "08-mobile-navegacao-inferior");
  await contexto.close();
}

// --- Administrador: tela de gestão e modal de confirmação de perigo ------------------------------
{
  const { contexto, page } = await sessao("admin@dev.local", "admin123");
  await page.goto(`${WEB}/equipe`);
  await page.waitForLoadState("networkidle");
  await page.mouse.move(LONGE.x, LONGE.y);
  await foto(page, "05-tela-de-gestao-equipe");

  // Diálogo de confirmação com ação de perigo: desativar um usuário (só abre; a ação nunca é confirmada).
  await page.getByRole("button", { name: /^desativar subgestora/i }).click();
  await page.mouse.move(LONGE.x, LONGE.y);
  await foto(page, "06-modal-de-confirmacao-de-perigo");
  await contexto.close();
}

await navegador.close();
