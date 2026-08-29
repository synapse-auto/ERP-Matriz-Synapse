import type { CitacaoMensagem } from "@/lib/atendimento/types";
import { previaExibida } from "@/lib/atendimento/citacao";
import type { Textos } from "@/lib/config/schema";

type TextosCitacao = Textos["atendimentos"]["mensagem"]["citacao"];

type Props = {
  citacao: CitacaoMensagem;
  textos: TextosCitacao;
};

/** Prévia persistida — texto React, nunca HTML da origem. */
export function CitacaoMensagemVisual({ citacao, textos }: Props) {
  const titulo =
    citacao.tipoReferencia === "ENCAMINHAMENTO"
      ? textos.encaminhamento
      : textos.resposta.replace("{autor}", citacao.autor.trim() || textos.origemIndisponivel);
  return (
    <div className="mb-1.5 rounded-md border-l-2 border-current/40 bg-background/15 px-2 py-1 text-xs">
      <p className="font-semibold">{titulo}</p>
      <p className="truncate opacity-80">
        {previaExibida(citacao, {
          imagem: textos.imagem,
          audio: textos.audio,
          documento: textos.documento,
          origemIndisponivel: textos.origemIndisponivel,
        })}
      </p>
    </div>
  );
}
