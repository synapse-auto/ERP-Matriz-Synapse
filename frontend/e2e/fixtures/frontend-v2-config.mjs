// Somente testes locais: entrega as configurações reais do repositório ao SSR.
// Não implementa API de negócio, login, proxy ou acesso a uma instância.
import { createServer } from "node:http";
import { readFileSync } from "node:fs";

const recursos = new URL("../../../backend/crm-app/src/main/resources/", import.meta.url);
const configuracoes = Object.fromEntries(["tema", "textos"].map((nome) => [
  "/api/v1/config/" + nome,
  readFileSync(new URL(nome + ".json", recursos)),
]));

createServer((pedido, resposta) => {
  // Controle em memória da fixture para testar o tema também no SSR; nunca persiste arquivos.
  if (pedido.method === "POST" && pedido.url === "/__fixture/tema") {
    let corpo = "";
    pedido.on("data", (parte) => { corpo += parte; });
    pedido.on("end", () => {
      try {
        configuracoes["/api/v1/config/tema"] = Buffer.from(JSON.stringify(JSON.parse(corpo)));
        resposta.writeHead(204).end();
      } catch {
        resposta.writeHead(400).end();
      }
    });
    return;
  }
  const corpo = configuracoes[pedido.url];
  resposta.writeHead(corpo ? 200 : 404, { "Content-Type": "application/json" });
  resposta.end(corpo ?? "{}");
}).listen(38981, "127.0.0.1");
