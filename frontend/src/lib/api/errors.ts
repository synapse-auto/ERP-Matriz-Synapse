/** Corpo de erro RFC 7807 (Problem Details), como o backend devolve em toda falha. */
export interface ProblemaHttp {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

/**
 * Erro de API tipado — nunca um `catch` silencioso. Toda chamada via {@link apiFetch} que falhar
 * lança isto, com a mensagem já pronta para exibir (nunca um stack trace cru na tela).
 */
export class ErroDeApi extends Error {
  readonly status: number;
  readonly problema: ProblemaHttp | null;

  constructor(status: number, problema: ProblemaHttp | null, mensagemPadrao: string) {
    super(problema?.detail ?? problema?.title ?? mensagemPadrao);
    this.status = status;
    this.problema = problema;
    this.name = "ErroDeApi";
  }
}
