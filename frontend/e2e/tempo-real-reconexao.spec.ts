import { expect, test, type Page, type WebSocketRoute } from "@playwright/test";

/**
 * Reconexão e status de entrega no navegador real, com o `@stomp/stompjs` de produção.
 *
 * Os testes unitários usam um dublê de cliente STOMP; aqui o cliente é o real e só o servidor é
 * simulado (`routeWebSocket` + `route`), o que torna a queda do socket e a ordem dos frames
 * determinísticas sem backend, banco ou produção. O HTML ainda é renderizado pelo Next, que busca
 * `/api/v1/config/tema` e `/textos` no servidor: aponte `SYNAPSE_BACKEND_URL` para um backend de dev
 * (ou qualquer serviço que devolva esses dois JSON).
 */

const ATENDIMENTO = "aaaaaaaa-0000-4000-8000-000000000001";
const LEAD = "bbbbbbbb-0000-4000-8000-000000000001";
const USUARIO = "11111111-1111-4111-8111-111111111111";
const ENVIADO_EM = "2026-09-22T12:00:00Z";

function jwt(payload: Record<string, unknown>): string {
  const base64 = (valor: unknown) => Buffer.from(JSON.stringify(valor)).toString("base64url");
  return `${base64({ alg: "none" })}.${base64(payload)}.assinatura`;
}

const cartao = {
  tipo: "CLIENTE",
  atendimentoId: ATENDIMENTO,
  atendimentoAtivoId: ATENDIMENTO,
  leadId: LEAD,
  leadNome: "Cliente Reconexão",
  leadFotoUrl: null,
  leadEmpresa: null,
  canalTipo: "WHATSAPP",
  etapaId: null,
  etapaNome: null,
  etapaCor: null,
  status: "EM_ATENDIMENTO",
  atendenteId: USUARIO,
  atendenteNome: "Ana",
  ultimaMensagemPreview: "orçamento enviado",
  ultimaMensagemRemetenteTipo: "ATENDENTE",
  ultimaMensagemEm: ENVIADO_EM,
  ultimaMensagemDoLeadEm: ENVIADO_EM,
  naoLidas: 0,
};

function mensagem(statusEntrega: string) {
  return {
    id: "cccccccc-0000-4000-8000-000000000001",
    atendimentoId: ATENDIMENTO,
    remetenteTipo: "ATENDENTE",
    remetenteId: USUARIO,
    remetenteNome: "Ana",
    tipo: "TEXTO",
    conteudo: "orçamento enviado",
    midiaUrl: null,
    midiaMetadados: null,
    opcoes: null,
    statusEntrega,
    erroEntrega: null,
    enviadoEm: ENVIADO_EM,
    citacao: null,
    idempotencyKey: "clique-1",
  };
}

/** Estado do "servidor": o que o REST devolve e as conexões STOMP que o navegador abriu. */
class ServidorFalso {
  statusPersistido = "PENDENTE";
  expiraEmSegundos = 900;
  renovacoes = 0;
  ultimoToken: string | null = null;
  readonly conexoes: {
    ws: WebSocketRoute;
    token: string | null;
    destinos: string[];
    fechadaPeloCliente: boolean;
  }[] = [];

  abertas() {
    return this.conexoes.filter((conexao) => !conexao.fechadaPeloCliente);
  }

  /** Conexões STOMP de fato ativas (CONNECTED e com assinaturas), ignorando as já fechadas. */
  assinadas() {
    return this.abertas().filter((conexao) => conexao.destinos.length > 0);
  }

  async instalar(page: Page) {
    await page.context().addCookies([{ name: "synapse_refresh", value: "e2e", url: "http://localhost" }]);
    await page.route("**/api/auth/refresh", (rota) => {
      this.renovacoes += 1;
      this.ultimoToken = jwt({ sub: USUARIO, papel: "ATENDENTE", renovacao: this.renovacoes });
      return rota.fulfill({
        json: {
          accessToken: this.ultimoToken,
          expiraEmSegundos: this.renovacoes === 1 ? this.expiraEmSegundos : 900,
        },
      });
    });
    await page.route("**/api/v1/**", (rota) => rota.fulfill(this.responder(new URL(rota.request().url()))));
    await page.routeWebSocket(/\/ws/, (ws) => this.aceitar(ws));
  }

  private responder(url: URL): { status?: number; json: unknown } {
    const caminho = url.pathname;
    if (caminho === "/api/v1/atendimentos/inbox") return { json: { itens: [cartao], proximoCursor: null } };
    if (caminho === "/api/v1/atendimentos/contagem") return { json: { MEUS: 1 } };
    if (caminho === "/api/v1/me") return { json: { id: USUARIO, nome: "Ana", email: "ana@e2e.local" } };
    if (caminho === `/api/v1/atendimentos/${ATENDIMENTO}/estado`) {
      return {
        json: {
          cartao,
          versao: 1,
          participantes: [],
          usuarioAtualEhResponsavel: true,
          usuarioAtualParticipa: true,
          podeEnviar: true,
        },
      };
    }
    if (caminho === `/api/v1/atendimentos/${ATENDIMENTO}/mensagens`) {
      return { json: { mensagens: [mensagem(this.statusPersistido)], proximoCursor: null } };
    }
    // Igual ao backend: `enviado_em > desde` não devolve a mensagem cujo status mudou.
    if (caminho === `/api/v1/atendimentos/${ATENDIMENTO}/mensagens/desde`) return { json: [] };
    if (caminho.endsWith("/leitura")) return { status: 204, json: {} };
    return { status: 404, json: {} };
  }

  private aceitar(ws: WebSocketRoute) {
    const token = new URL(ws.url()).searchParams.get("access_token");
    const conexao = { ws, token, destinos: [] as string[], fechadaPeloCliente: false };
    this.conexoes.push(conexao);
    ws.onClose(() => {
      conexao.fechadaPeloCliente = true;
    });
    ws.onMessage((dados) => {
      for (const frame of String(dados).split("\0")) {
        const [comando, ...linhas] = frame.replace(/^\n+/, "").split("\n");
        if (comando === "CONNECT" || comando === "STOMP") {
          ws.send("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0");
        } else if (comando === "SUBSCRIBE") {
          const destino = linhas.find((linha) => linha.startsWith("destination:"))?.slice("destination:".length);
          if (destino) conexao.destinos.push(destino);
        } else if (comando === "DISCONNECT") {
          // Como o broker do Spring: confirma o recibo, e só então o stompjs fecha o socket.
          const recibo = linhas.find((linha) => linha.startsWith("receipt:"))?.slice("receipt:".length);
          if (recibo) ws.send(`RECEIPT\nreceipt-id:${recibo}\n\n\0`);
        }
      }
    });
  }

  /** A queda que o broker provoca na conexão ativa: ERROR do STOMP e, em seguida, o close. */
  async derrubarComErro() {
    const [ativa] = this.assinadas();
    ativa.ws.send("ERROR\nmessage:queda simulada\n\n\0");
    await ativa.ws.close({ code: 1011, reason: "queda simulada" });
    ativa.fechadaPeloCliente = true;
  }
}

async function abrirConversa(page: Page) {
  await page.goto("/atendimentos");
  await page.getByText("Cliente Reconexão").first().click();
}

const DESTINOS_DA_CONVERSA = [
  "/user/queue/atendimento." + ATENDIMENTO,
  "/user/queue/notificacoes",
  "/user/queue/revogacoes",
];
/** Acima do teto do backoff (30s): dispara todo timer de reconexão que tiver sido agendado. */
const ALEM_DO_TETO_DO_BACKOFF_MS = 35_000;

test.describe("tempo real — reconexão e status sem F5", () => {
  test("ERROR + close gera uma única reconexão, reassina uma vez e limpa o 'Reconectando'", async ({ page }) => {
    const servidor = new ServidorFalso();
    await page.clock.install();
    await servidor.instalar(page);
    await abrirConversa(page);
    await expect.poll(() => servidor.assinadas().map((conexao) => conexao.destinos.length)).toEqual([3]);
    const conexoesAntesDaQueda = servidor.conexoes.length;

    await servidor.derrubarComErro();
    await expect(page.getByText("Reconectando...")).toBeVisible();
    await page.clock.runFor(ALEM_DO_TETO_DO_BACKOFF_MS);

    // Todo socket de reconexão nasce no mesmo tique do timer, antes de o primeiro completar o
    // CONNECT/SUBSCRIBE; quando um já assinou tudo, um segundo (o bug) já estaria registrado.
    await expect.poll(() => servidor.assinadas().some((conexao) => conexao.destinos.length === 3)).toBe(true);
    expect(servidor.conexoes.length - conexoesAntesDaQueda).toBe(1);
    expect([...servidor.assinadas()[0].destinos].sort()).toEqual(DESTINOS_DA_CONVERSA);
    await expect(page.getByText("Reconectando...")).toBeHidden();
  });

  test("status que mudou com o socket fora aparece após reconectar, sem F5", async ({ page }) => {
    const servidor = new ServidorFalso();
    await servidor.instalar(page);
    await abrirConversa(page);
    await expect(page.locator('[title="Enviando"]')).toBeVisible();

    servidor.statusPersistido = "ENVIADO"; // a outbox confirmou enquanto o socket estava fora
    await servidor.derrubarComErro();

    // Uma bolha só, agora confirmada: a reconciliação não duplica nem deixa a pendente para trás.
    await expect(page.locator('[title="Enviado"]')).toHaveCount(1);
    await expect(page.locator('[title="Enviando"]')).toHaveCount(0);
  });

  test("renovar o token troca a conexão sem deixar o socket do token antigo vivo", async ({ page }) => {
    const servidor = new ServidorFalso();
    // Primeiro token perto de vencer: a renovação proativa (30s antes) acontece logo após abrir.
    servidor.expiraEmSegundos = 31;
    await servidor.instalar(page);
    await abrirConversa(page);

    await expect.poll(() => servidor.renovacoes, { timeout: 15_000 }).toBeGreaterThanOrEqual(2);
    await expect.poll(() => servidor.assinadas().map((conexao) => conexao.token))
      .toEqual([servidor.ultimoToken]);
    await expect(page.getByText("Reconectando...")).toBeHidden();
  });
});
