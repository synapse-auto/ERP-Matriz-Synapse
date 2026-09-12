import type { QueryClient } from "@tanstack/react-query";

import { obterEstadoAtendimento } from "./api";
import type {
  EstadoAtendimentoSelecionado,
  EventoCanonicoAtendimentoTempoReal,
} from "./types";

export const chaveEstadoAtendimento = (atendimentoId: string) =>
  ["atendimentos", "estado", atendimentoId] as const;

const LIMITE_EVENTOS_PROCESSADOS = 2048;

/**
 * Unico reconciliador de estado operacional da tela.
 *
 * Eventos nao alteram owner/status/participantes diretamente: apenas solicitam um snapshot REST.
 * A versao por atendimento descarta repeticoes e eventos atrasados; chamadas concorrentes sao
 * colapsadas e, se uma versao maior chegar durante o fetch, o ciclo busca novamente antes de
 * liberar o composer.
 */
export class ReconciliadorEstadoAtendimento {
  private readonly eventosProcessados = new Set<string>();
  private readonly versoes = new Map<string, number>();
  private readonly versoesPendentes = new Map<string, number>();
  private readonly sincronizacoes = new Map<
    string,
    Promise<EstadoAtendimentoSelecionado>
  >();

  constructor(private readonly cache: QueryClient) {}

  registrarSnapshot(snapshot: EstadoAtendimentoSelecionado): void {
    const atendimentoId = snapshot.cartao.atendimentoId;
    const anterior = this.versoes.get(atendimentoId) ?? -1;
    if (snapshot.versao >= anterior) this.versoes.set(atendimentoId, snapshot.versao);
  }

  deveReconciliar(evento: EventoCanonicoAtendimentoTempoReal): boolean {
    return !this.eventosProcessados.has(evento.eventoId)
      && evento.dados.versao > this.versaoConhecida(evento.dados.atendimentoId);
  }

  registrarSemSnapshot(evento: EventoCanonicoAtendimentoTempoReal): boolean {
    if (this.eventosProcessados.has(evento.eventoId)) return false;
    this.lembrarEvento(evento.eventoId);
    const atendimentoId = evento.dados.atendimentoId;
    if (evento.dados.versao <= this.versaoConhecida(atendimentoId)) return false;
    this.versoes.set(atendimentoId, evento.dados.versao);
    this.invalidarListas();
    return true;
  }

  async receber(
    evento: EventoCanonicoAtendimentoTempoReal,
  ): Promise<EstadoAtendimentoSelecionado | null> {
    if (this.eventosProcessados.has(evento.eventoId)) return null;
    this.lembrarEvento(evento.eventoId);

    const atendimentoId = evento.dados.atendimentoId;
    const versaoAtual = this.versaoConhecida(atendimentoId);
    if (evento.dados.versao <= versaoAtual) return null;

    const pendente = this.versoesPendentes.get(atendimentoId) ?? -1;
    this.versoesPendentes.set(
      atendimentoId,
      Math.max(pendente, evento.dados.versao),
    );
    return this.sincronizar(atendimentoId);
  }

  async sincronizar(atendimentoId: string): Promise<EstadoAtendimentoSelecionado> {
    const existente = this.sincronizacoes.get(atendimentoId);
    if (existente) return existente;

    const sincronizacao = this.buscarAteAlcancarVersaoPendente(atendimentoId)
      .finally(() => this.sincronizacoes.delete(atendimentoId));
    this.sincronizacoes.set(atendimentoId, sincronizacao);
    return sincronizacao;
  }

  private async buscarAteAlcancarVersaoPendente(
    atendimentoId: string,
  ): Promise<EstadoAtendimentoSelecionado> {
    let snapshot: EstadoAtendimentoSelecionado;
    do {
      const alvoAntesDaLeitura = this.versoesPendentes.get(atendimentoId) ?? -1;
      snapshot = await obterEstadoAtendimento(atendimentoId);
      const conhecida = this.versaoConhecida(atendimentoId);
      if (snapshot.versao >= conhecida) {
        this.cache.setQueryData(chaveEstadoAtendimento(atendimentoId), snapshot);
        this.registrarSnapshot(snapshot);
      }
      const pendente = this.versoesPendentes.get(atendimentoId) ?? -1;
      if (snapshot.versao >= pendente) {
        this.versoesPendentes.delete(atendimentoId);
        break;
      }
      if (pendente <= alvoAntesDaLeitura) {
        throw new Error(`Snapshot ${atendimentoId} anterior ao evento canônico recebido`);
      }
    } while (true);

    this.invalidarListas();
    return snapshot;
  }

  private invalidarListas(): void {
    void this.cache.invalidateQueries({
      predicate: (consulta) => {
        const [raiz, tipo] = consulta.queryKey;
        return raiz === "atendimentos" && tipo !== "estado" && tipo !== "cartao";
      },
    });
  }

  private versaoConhecida(atendimentoId: string): number {
    const cache = this.cache.getQueryData<EstadoAtendimentoSelecionado>(
      chaveEstadoAtendimento(atendimentoId),
    );
    if (cache) this.registrarSnapshot(cache);
    return this.versoes.get(atendimentoId) ?? -1;
  }

  private lembrarEvento(eventoId: string): void {
    this.eventosProcessados.add(eventoId);
    if (this.eventosProcessados.size <= LIMITE_EVENTOS_PROCESSADOS) return;
    const maisAntigo = this.eventosProcessados.values().next().value;
    if (maisAntigo) this.eventosProcessados.delete(maisAntigo);
  }
}
