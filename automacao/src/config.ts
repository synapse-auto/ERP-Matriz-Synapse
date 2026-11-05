export interface ConfiguracaoAutomacao {
  readonly host: string;
  readonly port: number;
  readonly automacaoHabilitada: boolean;
  readonly followUpHabilitado: boolean;
}

function lerBooleano(nome: string, valor: string | undefined): boolean {
  if (valor === undefined || valor === '') return false;
  if (valor === 'true') return true;
  if (valor === 'false') return false;
  throw new Error(`${nome} deve ser true ou false`);
}

function lerPorta(valor: string | undefined): number {
  if (valor === undefined || valor === '') return 3100;
  const porta = Number(valor);
  if (!/^\d+$/.test(valor) || !Number.isInteger(porta) || porta < 1 || porta > 65535) {
    throw new Error('AUTOMACAO_PORT deve ser um inteiro entre 1 e 65535');
  }
  return porta;
}

export function carregarConfiguracao(env: NodeJS.ProcessEnv): ConfiguracaoAutomacao {
  const automacaoHabilitada = lerBooleano('AUTOMACAO_HABILITADA', env.AUTOMACAO_HABILITADA);
  // Validar ambas mesmo com a chave geral desligada evita esconder erros de configuracao.
  const followUpSolicitado = lerBooleano(
    'AUTOMACAO_FOLLOWUP_HABILITADO',
    env.AUTOMACAO_FOLLOWUP_HABILITADO,
  );
  return {
    host: env.AUTOMACAO_HOST || '127.0.0.1',
    port: lerPorta(env.AUTOMACAO_PORT),
    automacaoHabilitada,
    followUpHabilitado: automacaoHabilitada && followUpSolicitado,
  };
}
