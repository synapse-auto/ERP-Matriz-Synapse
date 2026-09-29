import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

/**
 * Quantas vezes a lista de conversas do chat interno é buscada por UMA mensagem recebida.
 *
 * A lista custa caro no banco (docs/49): cada GET recalcula não lidas de todas as conversas do
 * usuário sob RLS. Cada evento de tempo real deve gerar no máximo uma nova busca por tela aberta.
 * Pré-requisitos: backend dev em :8080 e frontend em :3000 (mesmos de gestao.spec.ts).
 */
const API = process.env.PLAYWRIGHT_API_URL ?? "http://localhost:8080";
const ANA = { email: "ana@dev.local", senha: "atendente123" };
const GESTOR = { email: "gestor@dev.local", senha: "gestor123" };
const LISTA = /\/api\/v1\/chat-interno\/conversas$/;
const ESPERA_DOS_EFEITOS_MS = 4_000;

async function tokenDe(request: APIRequestContext, { email, senha }: { email: string; senha: string }) {
  const resposta = await request.post(`${API}/api/v1/auth/login`, { data: { email, senha } });
  expect(resposta.ok()).toBeTruthy();
  return (await resposta.json()).accessToken as string;
}

async function entrar(page: Page, { email, senha }: { email: string; senha: string }) {
  await page.goto("/login");
  await page.getByLabel(/e-?mail/i).fill(email);
  await page.locator('input[type="password"]').fill(senha);
  await page.getByRole("button", { name: /entrar/i }).click();
  await expect(page).toHaveURL(/\/atendimentos/);
}

async function conversaDiretaComAna(request: APIRequestContext, tokenGestor: string) {
  const contatos = await (await request.get(`${API}/api/v1/chat-interno/contatos`, {
    headers: { Authorization: `Bearer ${tokenGestor}` },
  })).json();
  const ana = contatos.find((contato: { nome: string }) => /^ana/i.test(contato.nome));
  const resposta = await request.post(`${API}/api/v1/chat-interno/conversas/direta`, {
    headers: { Authorization: `Bearer ${tokenGestor}` },
    data: { usuarioId: ana.id },
  });
  expect(resposta.ok()).toBeTruthy();
  return (await resposta.json()).id as string;
}

test("uma mensagem recebida com o chat interno aberto busca a lista de conversas no máximo uma vez", async ({ page, request }) => {
  const tokenGestor = await tokenDe(request, GESTOR);
  const conversaId = await conversaDiretaComAna(request, tokenGestor);

  await entrar(page, ANA);
  await page.goto(`/chat-interno?conversaId=${conversaId}`);
  await page.waitForLoadState("networkidle");

  const buscas: number[] = [];
  page.on("request", (requisicao) => {
    if (requisicao.method() === "GET" && LISTA.test(new URL(requisicao.url()).pathname)) buscas.push(Date.now());
  });

  const texto = `medição ${Date.now()}`;
  const envio = await request.post(`${API}/api/v1/chat-interno/conversas/${conversaId}/mensagens`, {
    headers: { Authorization: `Bearer ${tokenGestor}` },
    data: { conteudo: texto },
  });
  expect(envio.ok()).toBeTruthy();
  // A mensagem continua chegando à conversa aberta, agora só pelo ouvinte global.
  await expect(page.getByText(texto).first()).toBeVisible({ timeout: 10_000 });
  await page.waitForTimeout(ESPERA_DOS_EFEITOS_MS);

  console.log(`[medicao] GET /chat-interno/conversas por mensagem recebida: ${buscas.length}`);
  expect(buscas.length).toBeGreaterThanOrEqual(1);
  expect(buscas.length).toBeLessThanOrEqual(1);
});
