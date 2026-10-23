import { describe, expect, it } from "vitest";

import { ErroDeApi } from "@/lib/api/errors";

import { mensagemDeErroDeTemplate } from "./erro-de-template";

const TEXTOS = {
  semPermissao: "Sem permissão.",
  naoEncontrado: "Não existe mais.",
  invalido: "Pedido inválido: {motivo}",
  recusado: "A Meta recusou: {motivo}",
  recusadoSemMotivo: "A Meta recusou.",
  indisponivel: "Provedor indisponível.",
  generico: "Não foi possível.",
  exclusaoCredencial: "Credencial recusada.",
  exclusaoPermissao: "Permissão da Meta recusada.",
  exclusaoRecurso: "Recurso não disponível.",
  exclusaoRecusada: "Exclusão recusada.",
  exclusaoIncerta: "Confira o estado antes de repetir.",
};

function erro(status: number, detail?: string) {
  return new ErroDeApi(status, detail === undefined ? null : { status, detail }, `Erro ${status}`);
}

describe("mensagemDeErroDeTemplate", () => {
  it.each([
    ["CREDENCIAL_RECUSADA", "Credencial recusada."],
    ["PERMISSAO_RECUSADA", "Permissão da Meta recusada."],
    ["RECURSO_INDISPONIVEL", "Recurso não disponível."],
    ["RECUSADO", "Exclusão recusada."],
    ["RESULTADO_INCERTO", "Confira o estado antes de repetir."],
    ["PROVEDOR_INDISPONIVEL", "Provedor indisponível."],
  ])("traduz exclusão %s sem exibir mensagem livre", (codigo, esperado) => {
    const falha = new ErroDeApi(codigo === "RESULTADO_INCERTO" ? 503 : 422,
      { codigo: "TEMPLATE_EXCLUSAO_" + codigo, detail: "Bearer segredo; conteúdo do template" }, "Falha");
    expect(mensagemDeErroDeTemplate(falha, TEXTOS)).toBe(esperado);
  });
  it("403 vira falta de permissão, sem repetir o detalhe técnico do Spring", () => {
    expect(mensagemDeErroDeTemplate(erro(403, "Access Denied"), TEXTOS)).toBe("Sem permissão.");
  });

  it("404 avisa que o template não existe mais", () => {
    expect(mensagemDeErroDeTemplate(erro(404, "template x nao encontrado"), TEXTOS)).toBe("Não existe mais.");
  });

  it("422 mostra o motivo que a Meta deu ao usuário", () => {
    expect(mensagemDeErroDeTemplate(erro(422, "Nome já em uso"), TEXTOS)).toBe("A Meta recusou: Nome já em uso");
  });

  it("422 sem motivo não mostra dois pontos vazios", () => {
    expect(mensagemDeErroDeTemplate(erro(422), TEXTOS)).toBe("A Meta recusou.");
  });

  it("400 mostra a validação do CRM", () => {
    expect(mensagemDeErroDeTemplate(erro(400, "template exige um corpo de texto"), TEXTOS))
      .toBe("Pedido inválido: template exige um corpo de texto");
  });

  it.each([500, 502, 503, 504])("%i vira provedor indisponível e não exibe o diagnóstico cru", (status) => {
    const mensagem = mensagemDeErroDeTemplate(erro(status, "<html>Bearer token</html> HTTP 200 text/html"), TEXTOS);
    expect(mensagem).toBe("Provedor indisponível.");
  });

  it("motivo com marcação ou longo demais é neutralizado", () => {
    const mensagem = mensagemDeErroDeTemplate(erro(422, `<html><body>${"x".repeat(500)}</body></html>`), TEXTOS);
    expect(mensagem).not.toContain("<");
    expect(mensagem.length).toBeLessThanOrEqual("A Meta recusou: ".length + 200);
  });

  it("falha de rede (sem ErroDeApi) cai no genérico", () => {
    expect(mensagemDeErroDeTemplate(new TypeError("Failed to fetch"), TEXTOS)).toBe("Não foi possível.");
  });
});
