// Reprodução do convite colaborativo contra backend e PostgreSQL reais (perfil dev).
//
// Pré-requisitos: backend no ar (API_URL), fixture `e2e/fixtures/convite-colaborativo.sql`
// aplicada, e `PSQL` apontando para um comando que execute SQL no MESMO banco do backend
// como dono (sem RLS) — por exemplo:
//   PSQL="docker exec -i synapse-postgres psql -U synapse -d synapse_convite -At"
//
// Uso: API_URL=http://localhost:8091 node e2e/_reproduzir-convite-colaborativo.mjs
//
// Em cada passo imprime: responsável do lead, responsável do atendimento, pedidos de entrada,
// participantes ativos, o que A/B/C enxergam pela API e os eventos WebSocket de cada sessão.
// Sai com código 1 se algum passo trocar o responsável sem uma transferência explícita.
import { execSync } from "node:child_process";
import { Client } from "@stomp/stompjs";

const API = process.env.API_URL ?? "http://localhost:8091";
const PSQL = process.env.PSQL ?? "docker exec -i synapse-postgres psql -U synapse -d synapse_convite -At";
const LEAD = "c0c00000-0000-4000-8000-000000000001";
const ATENDIMENTO = "c0c00000-0000-4000-8000-0000000000a1";
const SENHA = "atendente123";
const USUARIOS = {
  A: "ana@dev.local",
  B: "bruno@dev.local",
  C: "caio.convite@dev.local",
};

function sql(consulta) {
  return execSync(PSQL, { input: consulta, encoding: "utf8" }).trim();
}

async function chamar(token, metodo, caminho, corpo) {
  const resposta = await fetch(`${API}${caminho}`, {
    method: metodo,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "Idempotency-Key": crypto.randomUUID(),
    },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  });
  const texto = await resposta.text();
  let json = null;
  try { json = texto ? JSON.parse(texto) : null; } catch { json = texto; }
  return { status: resposta.status, json };
}

async function entrar(email) {
  const resposta = await fetch(`${API}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, senha: SENHA }),
  });
  if (!resposta.ok) throw new Error(`login ${email}: ${resposta.status}`);
  return (await resposta.json()).accessToken;
}

function corpoDe(mensagem) {
  try { return JSON.parse(mensagem.body); } catch { return mensagem.body; }
}

function conectar(rotulo, token, eventos) {
  return new Promise((resolve, reject) => {
    const cliente = new Client({
      webSocketFactory: () => new WebSocket(`${API.replace(/^http/, "ws")}/ws?access_token=${encodeURIComponent(token)}`),
      reconnectDelay: 0,
      onConnect: () => {
        const registrar = (fila) => (mensagem) => {
          const corpo = corpoDe(mensagem);
          eventos.push({ sessao: rotulo, fila, tipo: corpo?.tipo ?? corpo?.evento ?? "?", corpo });
        };
        cliente.subscribe("/user/queue/revogacoes", registrar("revogacoes"));
        cliente.subscribe("/user/queue/notificacoes", registrar("notificacoes"));
        resolve(cliente);
      },
      onStompError: (frame) => eventos.push({ sessao: rotulo, fila: "erro", tipo: frame.headers.message }),
      onWebSocketError: reject,
    });
    cliente.activate();
  });
}

function assinarConversa(rotulo, cliente, eventos) {
  cliente.subscribe(`/user/queue/atendimento.${ATENDIMENTO}`, (mensagem) => {
    const corpo = corpoDe(mensagem);
    eventos.push({ sessao: rotulo, fila: "atendimento", tipo: corpo?.tipo ?? "?", corpo });
  });
}

const esperar = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function estado(tokens, nomes) {
  const [leadDono, atendimentoDono] = sql(
    `SELECT l.atendente_responsavel_id, a.atendente_id FROM lead l JOIN atendimento a ON a.lead_id = l.id WHERE a.id = '${ATENDIMENTO}';`,
  ).split("|");
  const pedidos = sql(
    `SELECT tipo || ':' || status || ':' || solicitante_id FROM pedido_entrada_atendimento WHERE atendimento_id = '${ATENDIMENTO}' ORDER BY solicitado_em;`,
  ).split("\n").filter(Boolean);
  const participantes = sql(
    `SELECT usuario_id FROM atendimento_participante WHERE atendimento_id = '${ATENDIMENTO}' AND saiu_em IS NULL;`,
  ).split("\n").filter(Boolean);
  const visibilidade = {};
  for (const [rotulo, token] of Object.entries(tokens)) {
    const historico = await chamar(token, "GET", `/api/v1/atendimentos/${ATENDIMENTO}/mensagens`);
    visibilidade[rotulo] = historico.status;
  }
  const nome = (id) => nomes[id] ?? id ?? "∅";
  return {
    leadResponsavel: nome(leadDono),
    atendimentoResponsavel: nome(atendimentoDono),
    pedidos: pedidos.map((p) => p.replace(/[0-9a-f-]{36}$/, (id) => nome(id))),
    participantesAtivos: participantes.map(nome),
    historicoHttp: visibilidade,
  };
}

async function main() {
  const tokens = {};
  for (const [rotulo, email] of Object.entries(USUARIOS)) tokens[rotulo] = await entrar(email);
  const ids = Object.fromEntries(
    sql(`SELECT email || '|' || id FROM usuario WHERE email IN ('${Object.values(USUARIOS).join("','")}');`)
      .split("\n").map((linha) => linha.split("|")),
  );
  const nomes = Object.fromEntries(Object.entries(USUARIOS).map(([rotulo, email]) => [ids[email], rotulo]));
  const idDe = (rotulo) => ids[USUARIOS[rotulo]];

  const eventos = [];
  const clientes = { A: await conectar("A", tokens.A, eventos), B: await conectar("B", tokens.B, eventos) };
  assinarConversa("A", clientes.A, eventos);

  const passos = [];
  let ultimoEvento = 0;
  async function registrar(passo, http) {
    await esperar(1500);
    const novos = eventos.slice(ultimoEvento).map((e) => `${e.sessao}/${e.fila}:${e.tipo}`);
    ultimoEvento = eventos.length;
    const foto = await estado(tokens, nomes);
    passos.push({ passo, http, ...foto, eventos: novos });
    console.log(JSON.stringify({ passo, http, ...foto, eventos: novos }));
  }

  await registrar("0. inicial (A responsável)", null);

  const convite = await chamar(tokens.A, "POST", `/api/v1/atendimentos/${ATENDIMENTO}/convidar`, { atendenteId: idDe("B") });
  await registrar("1. A convida B", convite.status);

  const pendentesB = await chamar(tokens.B, "GET", "/api/v1/atendimentos?visao=PENDENTES");
  const cartaoNaFila = JSON.stringify(pendentesB.json ?? "").includes(ATENDIMENTO);
  const meuPedido = await chamar(tokens.B, "GET", `/api/v1/atendimentos/${ATENDIMENTO}/pedido-entrada/meu`);
  await registrar(`2. B vê o convite (Pendentes=${cartaoNaFila}, meuPedido=${meuPedido.json?.status})`, pendentesB.status);

  const aceite = await chamar(tokens.B, "POST", `/api/v1/atendimentos/pedidos-entrada/${meuPedido.json?.id}/aprovar`);
  await registrar("3. B aceita", aceite.status);

  assinarConversa("B", clientes.B, eventos);
  await registrar("4. A e B abrem a conversa", null);

  const envioB = await chamar(tokens.B, "POST", "/api/v1/atendimentos/mensagens", {
    leadId: LEAD, atendimentoId: ATENDIMENTO, conteudo: "Olá, aqui é o B ajudando.",
  });
  await registrar(`5. B envia (transferiuOLead=${envioB.json?.transferiuOLead})`, envioB.status);

  const envioA = await chamar(tokens.A, "POST", "/api/v1/atendimentos/mensagens", {
    leadId: LEAD, atendimentoId: ATENDIMENTO, conteudo: "Aqui é a A, sigo com você.",
  });
  await registrar(`6. A envia (transferiuOLead=${envioA.json?.transferiuOLead})`, envioA.status);

  const envioC = await chamar(tokens.C, "POST", "/api/v1/atendimentos/mensagens", {
    leadId: LEAD, atendimentoId: ATENDIMENTO, conteudo: "C não convidado tentando enviar.",
  });
  await registrar("7. C (não convidado) tenta enviar", envioC.status);

  Object.values(clientes).forEach((cliente) => cliente.deactivate());

  const inicial = passos[0];
  const trocou = passos.find(
    (p) => p.leadResponsavel !== inicial.leadResponsavel || p.atendimentoResponsavel !== inicial.atendimentoResponsavel,
  );
  console.log(trocou
    ? `RESULTADO: o responsável mudou no passo "${trocou.passo}" (lead=${trocou.leadResponsavel}, atendimento=${trocou.atendimentoResponsavel}).`
    : "RESULTADO: nenhum passo alterou o responsável; A continua responsável.");
  process.exit(trocou ? 1 : 0);
}

main().catch((erro) => {
  console.error(erro);
  process.exit(2);
});
