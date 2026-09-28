// Stub local da Graph API da Meta para os E2E de templates. Nunca fala com a Meta real.
//
// O backend roda com o adaptador de produção (WHATSAPP_PROVEDOR=meta-cloud) apontado para cá por
// WHATSAPP_URL_BASE, então o que se testa é o caminho real navegador → API → adaptador → provedor.
//
// Controle (usado só pelos testes):
//   GET  /__controle/chamadas          -> chamadas recebidas (método, caminho, query, corpo)
//   POST /__controle/reset             -> templates iniciais e nenhuma chamada registrada
//   POST /__controle/falhar            -> {"operacao":"excluir|editar|criar","status":400,"atrasoMs":0}
import http from "node:http";

const PORTA = Number(process.env.GRAPH_STUB_PORTA ?? 8089);

const INICIAIS = () => [
  template("tpl-e2e-1", "boas_vindas_e2e", "APPROVED", "Olá {{1}}, seja bem-vindo."),
  template("tpl-e2e-2", "retorno_orcamento_e2e", "APPROVED", "Seu orçamento ficou pronto."),
  template("tpl-e2e-3", "promo_rejeitada_e2e", "REJECTED", "Promoção."),
];

let templates = INICIAIS();
let chamadas = [];
let falhas = {};
let sequencia = 100;

function template(id, name, status, texto) {
  return {
    id,
    name,
    language: "pt_BR",
    status,
    category: "UTILITY",
    components: [{ type: "BODY", text: texto }],
  };
}

function responder(res, status, corpo) {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(corpo));
}

function erroDaMeta(status) {
  return {
    error: {
      message: "Recusado pelo stub",
      type: "OAuthException",
      code: status,
      error_user_msg: "Template recusado pelo provedor de teste",
    },
  };
}

async function lerCorpo(req) {
  const partes = [];
  for await (const parte of req) partes.push(parte);
  const texto = Buffer.concat(partes).toString("utf8");
  if (!texto) return null;
  try {
    return JSON.parse(texto);
  } catch {
    return texto;
  }
}

async function aplicarFalha(operacao, res) {
  const falha = falhas[operacao];
  if (!falha) return false;
  if (falha.atrasoMs) await new Promise((resolver) => setTimeout(resolver, falha.atrasoMs));
  if (!falha.status) return false;
  responder(res, falha.status, erroDaMeta(falha.status));
  return true;
}

const servidor = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORTA}`);
  const caminho = url.pathname.replace(/^\/v\d+\.\d+/, "");
  const corpo = await lerCorpo(req);

  if (caminho.startsWith("/__controle/")) {
    if (caminho === "/__controle/chamadas") return responder(res, 200, chamadas);
    if (caminho === "/__controle/reset") {
      templates = INICIAIS();
      chamadas = [];
      falhas = {};
      return responder(res, 200, { ok: true });
    }
    if (caminho === "/__controle/falhar") {
      falhas[corpo.operacao] = { status: corpo.status ?? null, atrasoMs: corpo.atrasoMs ?? 0 };
      return responder(res, 200, { ok: true });
    }
    return responder(res, 404, {});
  }

  // Autorização nunca é registrada: o teste só precisa de método, caminho e parâmetros.
  chamadas.push({ metodo: req.method, caminho, query: Object.fromEntries(url.searchParams), corpo });

  const partes = caminho.split("/").filter(Boolean);
  // /{waba}/message_templates
  if (partes.length === 2 && partes[1] === "message_templates") {
    if (req.method === "GET") return responder(res, 200, { data: templates });
    if (req.method === "POST") {
      if (await aplicarFalha("criar", res)) return;
      const novo = template(`tpl-e2e-${++sequencia}`, corpo.name, "PENDING", corpo.components?.[0]?.text ?? "");
      novo.language = corpo.language;
      templates.push(novo);
      return responder(res, 200, { id: novo.id, status: "PENDING", category: corpo.category });
    }
    if (req.method === "DELETE") {
      if (await aplicarFalha("excluir", res)) return;
      const antes = templates.length;
      templates = templates.filter(
        (t) => !(t.id === url.searchParams.get("hsm_id") && t.name === url.searchParams.get("name")),
      );
      return antes === templates.length
        ? responder(res, 400, erroDaMeta(400))
        : responder(res, 200, { success: true });
    }
  }
  // /{phone}/messages
  if (partes.length === 2 && partes[1] === "messages" && req.method === "POST") {
    return responder(res, 200, {
      messaging_product: "whatsapp",
      contacts: [{ input: corpo?.to, wa_id: corpo?.to }],
      messages: [{ id: `wamid.e2e.${++sequencia}` }],
    });
  }
  // /{id}
  if (partes.length === 1) {
    const alvo = templates.find((t) => t.id === partes[0]);
    if (!alvo) return responder(res, 404, erroDaMeta(404));
    if (req.method === "GET") return responder(res, 200, { status: alvo.status, components: alvo.components });
    if (req.method === "POST") {
      if (await aplicarFalha("editar", res)) return;
      alvo.components = corpo.components.map(({ type, text }) => ({ type, text }));
      return responder(res, 200, { success: true });
    }
  }
  responder(res, 404, erroDaMeta(404));
});

servidor.listen(PORTA, "127.0.0.1", () => {
  console.log(`graph-meta-stub ouvindo em http://127.0.0.1:${PORTA}`);
});
