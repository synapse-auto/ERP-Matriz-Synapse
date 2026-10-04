# Gestão: Equipe, Permissões e Exceções por usuário

Entrega da área **Gestão** (`/gestao`): integrantes, permissões por perfil e exceções individuais,
com persistência, autorização no backend, revalidação de sessão, auditoria e tela. Referências
visuais: `Gestao - Estrutural Vidros.html` e as capturas `01`–`06` do pacote de 26/09. O protótipo
é referência de **estrutura e interação**, não de segurança: as divergências estão na seção 11.

## 1. Regra de ouro

**O papel continua sendo o teto.** Cada capacidade do catálogo tem como teto exatamente o
`hasAnyRole(...)` que o caso de uso já tinha antes desta etapa. A configuração de Gestão só
**restringe dentro do teto** — nunca amplia. Ampliar (por exemplo, deixar atendente editar
template, hoje exclusivo de SUBGESTOR+) é decisão de produto, não um interruptor, e fica na
seção 13.

Exceção única, pedida explicitamente: **delegação ao SUBGESTOR** (criar/editar/desativar
atendente, senha provisória de atendente, exceções de atendentes e o perfil ATENDENTE). Essas
capacidades estão no teto do SUBGESTOR, mas nascem **negadas**; só um GESTOR ou ADMINISTRADOR as
concede.

Consequência: uma instância que nunca abriu a tela de Gestão tem exatamente o acesso operacional
de antes. A V83 não semeia valores; o padrão vem do catálogo em código
(`PoliticaDePermissoesTest.padraoPreservaAcessoOperacional` prova, papel a papel).

## 2. Inventário de capacidades

Legenda de papéis: A = ATENDENTE, S = SUBGESTOR, G = GESTOR, D = ADMINISTRADOR.
O = OPERADOR, acrescentado com os tetos e padrões da seção 15; a coluna "Teto (antes)"
registra os papéis históricos, não concede visão global ao novo papel.
"Padrão" = efetivo sem nada salvo. Nível = nível mínimo do módulo que libera a ação.

### 2.1 Incluídas no catálogo

| Id | Módulo | Nível | Ponto de entrada | Caso de uso / método | Teto (antes) | Padrão | Delegável | Sensível |
|---|---|---|---|---|---|---|---|---|
| `atendimentos.ver` | Atendimentos | — | todas as leituras de atendimento | Specification + RLS (RN-CRM-01) | A S G D | **estrutural**: A = Meus (+IA), demais = Todos | — | — |
| `atendimentos.responder` | Atendimentos | Editar | `POST /atendimentos/mensagens`, `/mensagens/template`, `/{id}/mensagens/midia`, `/{id}/mensagens/{m}/encaminhamentos`, `PUT/DELETE .../reacao` | `EnviarMensagemUseCase` (8 humanos), `EnviarMidiaUseCase` (5), `EncaminharMensagemUseCase` (2), `Definir/RemoverReacaoDaMensagemUseCase` | A S G D | todos | sim | não |
| `atendimentos.iniciar_conversa` | Atendimentos | Editar | `POST /atendimentos/novo-contato` | `IniciarNovoContatoUseCase.executar` | A S G D | todos | sim | não |
| `atendimentos.abrir_para_contato` | Atendimentos | Editar | `POST /atendimentos/leads/{id}/novo` | `IniciarNovoContatoUseCase.abrirParaLeadExistente` | A S G D | todos | sim | não |
| `atendimentos.transferir` | Atendimentos | Editar | `POST /atendimentos/{id}/transferir` com destino | `TransferirAtendimentoUseCase.executar` (`#p1 != null`) | A S G D | todos | sim | não |
| `atendimentos.devolver_ia` | Atendimentos | Editar | `POST /atendimentos/{id}/transferir` sem destino | `TransferirAtendimentoUseCase.executar` (`#p1 == null`) | A S G D | todos | sim | não |
| `atendimentos.finalizar` | Atendimentos | Editar | `POST /atendimentos/{id}/finalizar` | `FinalizarAtendimentoUseCase.executar` | A S G D | todos | sim | não |
| `atendimentos.colaborar` | Atendimentos | Editar | `pedir-entrada`, `entrar`, `convidar` | `GerenciarParticipacaoAtendimentoUseCase.solicitar/solicitarPorLead/entrar/convidar` | A S G D | todos | sim | não |
| `atendimentos.finalizar_lote` | Atendimentos | Gerenciar | `GET/POST /atendimentos/finalizar-lote` | `FinalizarAtendimentosVisiveisUseCase` (2) + `FinalizarAtendimentoUseCase.executarEmLote` | A S G D | todos | **não** | sim |
| `contatos.editar` | Contatos | Editar | `PUT /leads/{id}` | `AtualizarLeadUseCase` | A S G D | todos | sim | não |
| `tags.aplicar` | Tags | Editar | `PUT/DELETE /leads/{id}/tags/{tag}` | `Vincular/DesvincularTagDoLeadUseCase` | A S G D | todos | sim | não |
| `tags.criar` | Tags | Gerenciar | `POST /tags` | `GestaoDeTagsUseCases.criar` | S G D | S G D | não | não |
| `tags.editar_excluir` | Tags | Gerenciar | `PUT/DELETE /tags/{id}` | `GestaoDeTagsUseCases.atualizar/remover` | S G D | S G D | não | sim |
| `mensagens_rapidas.usar` | Mensagens rápidas | Ver | `GET /mensagens-rapidas` | `ListarMensagensRapidasUseCase` | A S G D | todos | sim | não |
| `mensagens_rapidas.criar` | Mensagens rápidas | Editar | `POST /mensagens-rapidas` | `CriarMensagemRapidaUseCase` | A S G D | todos | sim | não |
| `mensagens_rapidas.editar_excluir` | Mensagens rápidas | Editar | `PUT/DELETE /mensagens-rapidas/{id}` | `Atualizar/RemoverMensagemRapidaUseCase` | A S G D | todos | sim | não |
| `templates.ver` | Templates | Ver | `GET /whatsapp/templates` | `ListarTemplatesWhatsAppUseCase` | A S G D | todos | sim | não |
| `templates.criar` | Templates | Editar | `POST /whatsapp/templates` | `CriarTemplateWhatsAppUseCase` | A S G D | todos | sim | não |
| `templates.editar` | Templates | Gerenciar | `PUT /whatsapp/templates/{id}` | `EditarTemplateWhatsAppUseCase` | S G D | S G D | não | não |
| `templates.excluir` | Templates | Gerenciar | `DELETE /whatsapp/templates/{id}` | `ExcluirTemplateWhatsAppUseCase` | S G D | S G D | não | sim |
| `resumo_ia.ver` | Resumo por IA | Ver | `GET /atendimentos/{id}/resumo-ia`; campo `resumoIa` de `GET /leads/{id}` e `/{id}/agenda` | `SolicitarResumoIaUseCase.estado`; `LeadController.ficha` | A S G D | todos | sim | não |
| `resumo_ia.solicitar` | Resumo por IA | Editar | `POST /atendimentos/{id}/resumo-ia` | `SolicitarResumoIaUseCase.executar` | A S G D | todos | sim | não |
| `dashboard.ver` | Dashboard (flag `dashboard`) | Ver | `GET /dashboard/visao-geral` | `ObterVisaoGeralDashboardUseCase` | S G D | S G D | não | não |
| `mensagens_programadas.ver` | Programadas | Ver | `GET /mensagens-programadas` | `ListarMensagensProgramadasUseCase` | A S G D | todos | sim | não |
| `mensagens_programadas.criar` | Programadas | Editar | `POST /mensagens-programadas` | `CriarMensagemProgramadaUseCase` | A S G D | todos | sim | não |
| `mensagens_programadas.editar_cancelar` | Programadas | Editar | `PUT /{id}`, `POST /{id}/cancelar` | `Atualizar/CancelarMensagemProgramadaUseCase` | A S G D | todos | sim | não |
| `lembretes.ver` | Lembretes | Ver | `GET /lembretes` | `ListarLembretesUseCase` | A S G D | todos | sim | não |
| `lembretes.criar` | Lembretes | Editar | `POST /lembretes` | `CriarLembreteUseCase` | A S G D | todos | sim | não |
| `lembretes.editar_excluir` | Lembretes | Editar | `PUT/DELETE /lembretes/{id}` | `Atualizar/RemoverLembreteUseCase` | A S G D | todos | sim | não |
| `automacao.ver` | Automação | Ver | `GET /automacao/config`, `/config/resumo-ia`, `/follow-ups`, `/fidelizacao`, `/telemetria` | `ListarConfiguracoesAutomacaoAdmin`, `ObterConfiguracaoResumoIa`, `ListarRegras*Admin`, `ObterStatusAutomacaoTelemetria` | S G D | S G D | não | não |
| `automacao.editar_parametros` | Automação | Gerenciar | `PUT /automacao/config/{chave}`, `PUT /config/resumo-ia` | `AtualizarConfiguracaoAutomacao`, `AtualizarConfiguracaoResumoIa` | S G D | S G D | não | sim |
| `automacao.regras` | Automação | Gerenciar | `POST/PUT/PATCH/DELETE /automacao/follow-ups*`, `/fidelizacao*` (regras) | `SalvarRegraFollowUp/Fidelizacao` (3+3), `AlternarRegra*` | S G D | S G D | não | não |
| `campanhas.ver` | Campanhas (flag `campanhas`) | Ver | `GET /campanhas`, `/templates`, `/{id}`, configuração e métricas agregadas | consultas existentes | O S G D | S G D (O OFF) | não | não |
| `campanhas.ver_destinatarios` | Campanhas | Ver | destinatários, conferência, CSV, opt-outs, excluídos; `POST /previa`, `/projecao` | consultas de público existentes | S G D | S G D | não | não |
| `campanhas.registrar_opt_out` | Campanhas | Editar | `PUT /campanhas/optouts/{leadId}` | `OptOutUseCases.registrar` | S G D | S G D | não | não |
| `campanhas.criar` | Campanhas | Editar | `POST /campanhas` | `CriarCampanhaUseCase` | D | D | não | não |
| `campanhas.editar` | Campanhas | Editar | `PUT /campanhas/{id}` | `AtualizarRascunhoUseCase` | D | D | não | não |
| `campanhas.testar` | Campanhas | Editar | `POST /campanhas/{id}/teste` | `EnviarTesteDeCampanhaUseCase` | D | D | não | não |
| `campanhas.operar` | Campanhas | Gerenciar | iniciar, pausar, retomar, cancelar, limite e interruptor | `IniciarCampanhaUseCase`, `ControleDaCampanhaUseCases` | D | D | não | sim |
| `campanhas.conferir` | Campanhas | Gerenciar | `POST /campanhas/{id}/conferencia/{destinatarioId}/conferido` | `ControleDaCampanhaUseCases.resolverConferencia` | D | D | não | não |
| `campanhas.configurar` | Campanhas | Gerenciar | `PUT /campanhas/configuracao` | `ConfiguracaoDeCampanhasUseCases.atualizar` | D | D | não | sim |
| `campanhas.opt_out` | Campanhas | Gerenciar | `DELETE /campanhas/optouts/{leadId}` | `OptOutUseCases.remover` | D | D | não | sim |
| `equipe.ver` | Gestão da equipe | Ver | `GET /usuarios`, `GET /equipe/desempenho`, leitura de `/gestao/permissoes/**` | `ListarUsuariosUseCase`, `ObterDesempenhoDaEquipeUseCase`, `AutorizacaoDeGestao.LER` | S G D | S G D | não | não |
| `equipe.disponibilidade_ia` | Gestão da equipe | Editar | `PATCH /usuarios/{id}/disponibilidade-ia` | `AtualizarDisponibilidadeParaIaUseCase` | S G D | S G D | não | não |
| `equipe.criar` | Gestão da equipe | Gerenciar | `POST /usuarios` | `CriarUsuarioUseCase` (S: só ATENDENTE) | G D **+ S delegado** | G D | não | sim |
| `equipe.editar` | Gestão da equipe | Gerenciar | `PUT /usuarios/{id}` | `AtualizarUsuarioUseCase` (S: só ATENDENTE, papel inalterado) | G D **+ S delegado** | G D | não | não |
| `equipe.alterar_papel` | Gestão da equipe | Gerenciar | `PUT /usuarios/{id}` quando o papel muda | `AtualizarUsuarioUseCase` (programático) | G D | G D | não | sim |
| `equipe.senha_provisoria` | Gestão da equipe | Gerenciar | `POST /usuarios/{id}/senha-provisoria` | `DefinirSenhaProvisoriaUseCase` (S: só ATENDENTE) | G D **+ S delegado** | G D | não | sim |
| `equipe.desativar` | Gestão da equipe | Gerenciar | `PATCH /usuarios/{id}/desativar` | `DesativarUsuarioUseCase` (S: só ATENDENTE) | G D **+ S delegado** | G D | não | sim |
| `equipe.excecoes_atendentes` | Gestão da equipe | Gerenciar | `PUT/DELETE /gestao/permissoes/usuarios/{id}/excecoes`, prévia de cópia | `SalvarExcecoes`, `RestaurarPadrao`, `PreverCopia.paraUsuario` | G D **+ S delegado** | G D | não | sim |
| `equipe.perfis` | Gestão da equipe | Gerenciar | `PUT /gestao/permissoes/perfis/{papel}`, prévia de cópia de perfil | `SalvarPerfilDePermissao` (S: só perfil ATENDENTE), `PreverCopia.paraPerfil` | G D **+ S delegado** | G D | não | sim |

`CapacidadesReferenciadasTest` reprova o build se um `@PreAuthorize` citar um id fora do catálogo
e se uma capacidade configurável não tiver nenhum ponto de aplicação.

### 2.2 Excluídas, com motivo

| Função existente | Motivo |
|---|---|
| Banco de Arquivos | Fora da primeira entrega (flag `banco_arquivos` desligada). Upload de anexo no chat **não** é Banco de Arquivos: está em `atendimentos.responder`. |
| Relatórios, Horários, sub-abas do Dashboard | Fora de escopo / flags desligadas. Nenhum controle "Em breve". Campanhas entrou no catálogo após E220. |
| Importação e exportação de leads (`/leads/importacao/**`, `/leads/exportar`) | Excluídas pelo pedido. Continuam com a regra que já tinham. |
| Visibilidade de leads/contatos/timeline/mídias | Estrutural (RN-CRM-01, Specification, RLS). Exibida como "regra fixa", nunca configurável. |
| "Assumir conversa de outro atendente" | Não é ação discreta: é consequência de RN-CRM-06 dentro do recorte. ATENDENTE não alcança lead de colega; configurar isso exigiria mudar a Specification. |
| Alcance "Equipe" | Não existe modelo de times; oferecer "Equipe" seria prometer um recorte que o sistema não faz. |
| Identidade/tema/logo da instância, fidelização (config) e datas festivas | Configurações de aparência estão fora da primeira entrega; fidelização/festivas já são exclusivas de G/D (não configuráveis). |
| Avaliações da equipe (`GET /equipe/avaliacoes`), audit log, feedbacks (lista), Administração | Exclusivas de G/D (ou só D). Não há o que restringir para perfis configuráveis; não são concedíveis. |
| Etapas do funil, campos customizados, canais, chat interno, presença própria | Não pedidos no escopo; continuam como estão. |

### 2.3 Decisões necessárias (não tomadas sozinho)

Ver seção 13 do relatório e a seção 13 deste documento.

## 3. Modelo de permissão

- **Nível por módulo** (Sem acesso / Ver / Editar / Gerenciar) é **preset e limite** ao mesmo
  tempo. Limite: ação com nível mínimo acima do nível do módulo fica bloqueada, mesmo com o
  interruptor ligado. Preset: escolher um nível na tela liga as ações até ele e desliga as acima
  (feito no rascunho; o servidor recebe valores explícitos e só valida).
- Módulos cuja leitura é estrutural (Atendimentos, Contatos, Tags) não oferecem "Sem acesso".
- Níveis acima do máximo útil do papel ficam desabilitados (ex.: ATENDENTE em Tags vai até Editar).
- O servidor recusa combinação incoerente: interruptor ligado com nível abaixo do mínimo
  (`NIVEL_INSUFICIENTE`). A tela desenha o **efetivo** (o que o backend calculou), nunca "ligado
  mas bloqueado".
- **Dependências** (ex.: criar mensagem rápida depende de usar) aparecem como bloqueio com motivo.
- **Exceção** por usuário: herdar (ausente) / permitir / negar, por ação e por nível de módulo.
  Exceção substitui o herdado, mas nunca supera teto, flag ou dependência. Restaurar apaga a linha.
- **Contagem**: cada chave persistida conta como uma exceção, inclusive as de nível. O badge da aba
  conta **usuários** com ao menos uma exceção válida (módulo com flag ligada); o do usuário conta
  as exceções persistidas válidas e marca "rascunho" enquanto há alteração não salva.
- **Mudança de papel** descarta todas as exceções do usuário (nenhum privilégio do papel anterior
  sobrevive) e grava `MUDANCA_DE_PAPEL` no histórico.
- **Exceções por usuário são opcionais por instância** (flag `gestao_excecoes`, seção 10). Desligada
  ou ausente — o padrão —, a aba aparece como "Em breve", o atalho de permissões da Equipe abre o
  perfil da função e toda gravação de exceção é recusada. Exceções já salvas continuam valendo.

## 4. Quem concede o quê

| Ator | Perfis | Exceções | Usuários |
|---|---|---|---|
| ADMINISTRADOR, GESTOR | editam SUBGESTOR, ATENDENTE e OPERADOR | de SUBGESTOR, ATENDENTE e OPERADOR | criar/editar/mudar papel/senha/desativar ATENDENTE, SUBGESTOR e OPERADOR |
| SUBGESTOR sem delegação | lê | lê ATENDENTES e as próprias | alterna disponibilidade IA (como antes) |
| SUBGESTOR delegado | com `equipe.perfis`: só o perfil ATENDENTE, nunca o próprio; mesmas regras das exceções; não copia perfil | só de ATENDENTE; regra de efeito abaixo | só ATENDENTE, nunca a si; nunca cria/promove SUBGESTOR; nunca muda papel |
| ATENDENTE, OPERADOR | — | — | — |

**Regra de efeito do SUBGESTOR delegado** (perfil ATENDENTE e exceções de ATENDENTE,
`PoliticaDeConcessao.exigirDentroDaDelegacao`, espelhada na tela por `violacaoDaDelegacao`): toda
ação cujo interruptor gravado **ou** efetivo muda precisa ser delegável e, para ir a ligado, ele
precisa tê-la. Nível de módulo é livre (decisão de 28/09) justamente porque entra por essa regra:
baixar Atendimentos para Editar desligaria o lote (não delegável) e é recusado; subir um nível ou
religar uma dependência que reativaria algo que ele não tem também é recusado. A prévia de cópia
para usuário continua sem transferir nível quando quem copia é o SUBGESTOR (mais restritiva que o
salvamento, nunca menos).

GESTOR e ADMINISTRADOR têm perfil **fixo** (tudo do teto) e não são alvo de exceção nem de edição
pela gestão comum — o que também impede um GESTOR de receber operação exclusiva de ADMINISTRADOR.
O formulário comum não oferece GESTOR nem ADMINISTRADOR (contrato `PapelGerenciavel`).

## 5. Persistência (V83)

| Tabela | Papel |
|---|---|
| `permissao_politica` | revisão global (linha única); sobe em toda mudança de acesso |
| `permissao_perfil` / `_item` | revisão por perfil + itens (`NIVEL`/`ACAO`) salvos |
| `permissao_usuario` / `_excecao` | revisão por usuário + exceções explícitas |
| `permissao_historico` | antes/depois, revisão, operação, autor, alvo, origem de cópia — gravado **na mesma transação** |

- **Concorrência**: salvar exige `revisaoEsperada`; `UPDATE ... WHERE revisao = ?` sem linha
  afetada ⇒ 409 com `revisaoAtual`, sem tocar em nada. Gravação de exceções trava a linha do
  usuário (`FOR SHARE`); mudança de papel/desativação trava `FOR UPDATE`: as duas se serializam.
- **Atomicidade**: todo o payload é validado antes da primeira escrita; qualquer violação ⇒ 422
  com a lista completa e nada persistido.
- **Flags**: salvar com um módulo desligado preserva as linhas dele; religar a flag volta a
  aplicá-las.
- **Triggers**: mudança de `papel`/`ativo` em `usuario` e qualquer escrita nas tabelas de item
  sobem a revisão global — inclusive correções manuais pelo `psql`.
- **Último responsável administrativo**: trigger com `pg_advisory_xact_lock` recusa desativar ou
  rebaixar o último GESTOR/ADMINISTRADOR ativo, inclusive em transações concorrentes. A API nem
  oferece essas operações sobre GESTOR/ADMINISTRADOR (404).
- Sem RLS nas tabelas novas: não há dado de lead; toda escrita passa por caso de uso com
  `@PreAuthorize`; sem `tenant_id` (Silo).

## 6. Aplicação (enforcement)

- `@PreAuthorize("<papel de antes> and @capacidades.permite('id')")` em 82 pontos. O bean
  `capacidades` (`VerificadorDeCapacidadesSpring`) decide com o papel e a situação do **banco**, não
  do JWT; id desconhecido lança (falha alta).
- **Técnico × humano**: caminhos `hasRole('SERVICO')` (Automação, outbox, jobs, programadas via
  `executarComoServico`) nunca passam por capacidade (`CapacidadesReferenciadasTest.servicoSemCapacidade`).
  Sem usuário em `ContextoDeServico`, `permite` devolve `true`: comando já aceito não é revalidado,
  então não há reenvio, duplicação nem descarte de outbox.
- Leitura de lead continua pela Specification/RLS; capacidade não amplia recorte
  (`GestaoPermissoesIT.excecaoNaoAlcancaLeadDeColega`).
- `/internal/v1` e `X-Synapse-Token`: intocados.

## 7. Sessão, cache e tempo real

| Mudança | Mesmo nó | Outro nó | JWT antigo | WebSocket |
|---|---|---|---|---|
| Permissão (perfil/exceção) | próxima chamada | ≤ `SYNAPSE_PERMISSOES_REVALIDACAO` (2s) | continua válido; capacidade recalculada | aviso `ACESSO_ALTERADO` → tela recarrega permissões |
| Papel alterado | próxima chamada: 401 `sessao-desatualizada` | ≤ 2s | refresh devolve token com o papel novo | assinaturas do usuário descartadas; handshake com token antigo recusado; revalidação por TTL compara papel atual |
| Desativação | próxima chamada: 401 | ≤ 2s | refresh recusado (tokens revogados) | idem |

- `ResolvedorDePermissoesEfetivas`: cache em memória por usuário, marcado com revisão global e
  flags. A revisão é relida no máximo a cada intervalo (uma consulta por nó, não por usuário);
  recálculo é uma ida ao banco por usuário e revisão. Leituras do cache usam o **pool do chat**:
  o caminho de toda requisição não pode esperar conexão atrás de relatório pesado no pool geral.
- **Redis fora do ar** não abre nem fecha nada: o cache revalida pela revisão do banco; o aviso
  em tempo real é publicado de forma assíncrona e com fila limitada (salvar não espera Redis —
  `GestaoTempoRealIT.redisForaNaoAbreNemTrava`). Falha de banco propaga (fecha).
- **Não prometido**: arquivos já baixados e URLs assinadas de mídia já emitidas (expiração do
  storage) não são recolhidos.

## 8. API (`/api/v1/gestao/permissoes`)

| Método | Rota | Quem |
|---|---|---|
| GET | `/minhas` | autenticado |
| GET | `/catalogo`, `/perfis`, `/usuarios`, `/usuarios/{id}` | G/D; S com `equipe.ver` (S vê ATENDENTES e a si) |
| PUT | `/perfis/{papel}` `{revisaoEsperada, niveis, acoes, copiadoDe?}` | G/D; S delegado (só `ATENDENTE`) |
| POST | `/perfis/{papel}/copia/previa` `{origem}` | G/D (S delegado: origem sempre fora da alçada, 422) |
| PUT | `/usuarios/{id}/excecoes` `{revisaoEsperada, niveis, acoes, copiadoDe?}` | G/D; S delegado |
| DELETE | `/usuarios/{id}/excecoes?revisaoEsperada=` | G/D; S delegado |
| POST | `/usuarios/{id}/copia/previa` `{origemUsuarioId}` | G/D; S delegado (origem ATENDENTE) |

Erros RFC 7807: `permissao-invalida` (422, `violacoes[{chave,codigo}]`), `concessao-negada` (403,
`codigo`), `revisao-desatualizada` (409, `revisaoAtual`), `sessao-desatualizada` (401). Com
`gestao_excecoes` desligada, gravar/restaurar exceções e a prévia de cópia para usuário respondem 422
`FLAG_DESLIGADA` (chave `gestao_excecoes`); as leituras continuam, com `editavel=false`. No perfil,
a revisão é conferida antes da alçada do SUBGESTOR: tela desatualizada recebe 409, não 403. Cópia é
sempre prévia + salvamento normal (com revisão e histórico `COPIAR`); origem GESTOR/ADMINISTRADOR
é recusada.

## 9. Tela

- Menu: item **Gestão** (`/gestao`) no grupo Gestão; `/equipe` redireciona. Itens Dashboard,
  Automação, Templates, Mensagens rápidas, Programadas e Lembretes somem quando a leitura foi
  revogada (efetivas do backend; sem elas, vale a regra por papel de antes).
- Selo do cabeçalho reflete o papel real (Gestão autorizada / Administrador / Acesso delegado /
  Somente leitura) — não "SOMENTE GESTOR".
- Salvar/Descartar com impacto, confirmação para alteração sensível, proteção ao sair (link e
  fechar aba) e ao trocar perfil/usuário/aba, sem duplo envio, erro sem perder rascunho, 409 com
  "recarregar versão atual" explícito. Nada é publicado antes da resposta do backend.

## 10. Ativação, desativação e rollback

- Não há flag que desligue o enforcement. Revogação salva é sempre aplicada; desligar uma feature
  flag de módulo só oculta o módulo e **preserva** as linhas salvas.
- **Rollback de imagem para antes da V83** ignora as tabelas `permissao_*` (o código antigo não as
  conhece): toda revogação salva deixa de valer. Antes de um rollback, liste o que seria perdido:

```sql
SELECT 'PERFIL' AS escopo, papel::text AS alvo, alvo AS chave, valor FROM permissao_perfil_item
UNION ALL
SELECT 'USUARIO', u.email, e.alvo, e.valor
  FROM permissao_usuario_excecao e JOIN usuario u ON u.id = e.usuario_id
 ORDER BY 1, 2, 3;
```

  e registre a decisão; a V83 não é revertida (as tabelas ficam, voltam a valer no redeploy).
- **Banco sem a V83** (banco novo que sobe pausado antes da V73, docs/41): o cache de permissões
  detecta a ausência das tabelas (`to_regclass`), registra `[PERMISSOES_SEM_V83]` uma vez e usa o
  padrão de cada papel, que é o acesso anterior à Gestão. As telas de Gestão só funcionam depois da
  V83. Quando as tabelas aparecem, a revisão muda (de `-1` para a real) e o cache se refaz sozinho.
- Variável nova, opcional: `SYNAPSE_PERMISSOES_REVALIDACAO` (default `2s`), já declarada com
  default no `dokploy-stack.yml`. Nenhuma ação obrigatória no Dokploy.
- **Exceções por usuário** saem "Em breve" (flag `gestao_excecoes` ausente ou falsa). Para ligar numa
  instância, sem deploy (vale em até `SYNAPSE_PERMISSOES_REVALIDACAO`):

```sql
INSERT INTO feature_flag (chave, habilitado, descricao)
VALUES ('gestao_excecoes', TRUE, 'Aba Excecoes por usuario em Gestao e gravacao de excecoes.')
ON CONFLICT (chave) DO UPDATE SET habilitado = TRUE;
```

  Desligar de novo não apaga nem ignora exceções salvas: elas seguem no cálculo, só deixam de ser
  editáveis. Para saber se há alguma antes de desligar: `SELECT count(*) FROM permissao_usuario_excecao;`.

## 11. Divergências em relação ao protótipo

| Protótipo | Implementado | Por quê |
|---|---|---|
| Selo "SOMENTE GESTOR" | selo conforme papel real | ADMINISTRADOR e SUBGESTOR delegado também acessam |
| Três papéis | + ADMINISTRADOR (fixo, fora da grade como já era) | papel real do sistema |
| Números 51/49, 8/8, contadores fixos | contagens reais do catálogo e do banco | proibido dado mockado |
| Modal sem senha, com "Gestor" | senha inicial obrigatória; só Subgestor/Atendente (S delegado: só Atendente) | contrato atual; sem credencial implícita |
| Ver conversas com Não/Meus/Equipe/Todos | Meus/Todos exibidos como regra fixa | RN-CRM-01; não existe "Equipe" |
| "Assumir conversa de outro atendente" | ausente | ver 2.2 |
| Banco de Arquivos, Relatórios no menu | fora | escopo; Campanhas entrou no catálogo após E220 |
| Cópia de gestor para atendente | recusada; cópias sempre recortadas pelo teto com prévia | não transmitir limite superior |
| Exceção ligando "assumir colega"/"criar template" do atendente | impossível: "assumir" não existe; ações fora do teto são recusadas | teto |
| Toggle ligado com nível abaixo do mínimo | bloqueado e explicado | coerência |
| Botão reativar usuário | ausente | não existe endpoint de reativação |
| Select nativo "Copiar de outro usuário…" | `Seletor` acessível do projeto | consistência e teclado |
| Aba Exceções sempre disponível | "Em breve" atrás da flag `gestao_excecoes` | pedido do cliente em 28/09; é a única aba "Em breve" da Gestão (exceção deliberada à regra de 2.2) |
| Cartões de módulo em duas colunas alinhadas por linha | colunas independentes (masonry), no máximo duas | pedido do cliente em 27–28/09: sem vãos entre cartões |

## 12. Desempenho medido

Ver o relatório da entrega (`[GESTAO-MEDICAO]` do `GestaoPermissoesIT.desempenhoDoCache`): em
requisições quentes, zero recálculos; após alteração, exatamente um recálculo para o usuário
afetado. Cada operação de gestão faz uma consulta de contexto por usuário afetado que chamar a
API (lote) e uma leitura de revisão por nó por intervalo.

## 13. Pendências de decisão

1. Ampliar alguma ação além do teto atual (ex.: atendente editar template)?
2. Oferecer "Sem acesso" em Contatos/Tags exigiria cortar leituras hoje estruturais — manter?
3. Reativação de usuário desativado: criar endpoint?
4. ~~Nível de módulo delegável ao SUBGESTOR?~~ Decidido em 28/09: sim, pela regra de efeito (seção 4).
5. Alcance configurável para SUBGESTOR (Meus em vez de Todos) exigiria mudar Specification e RLS.

## 14. Permissões na interface

A tela pergunta ao backend, nunca ao papel. `useCapacidades()` (`frontend/src/lib/gestao/use-capacidades.ts`)
lê `/gestao/permissoes/minhas`, que já traz o efetivo de perfil + exceção + teto do papel + flags, e
expõe `pode(id)` e `alcancaTodos` (recorte estrutural de `atendimentos.ver`).

- **Estado seguro.** Sem resposta (carregando ou erro), `pode` é `false`: nenhuma ação privilegiada
  aparece para sumir depois. Com uma resposta já obtida, uma revalidação que falhe mantém a última.
- **Controle negado sai da tela.** Botão, item de menu, item do "⋯", atalho de teclado (`/` das
  respostas rápidas) e diálogo aberto fecham junto (os diálogos recebem `aberto && pode`). Campos de
  leitura permanecem: revogar criar template não esconde a lista.
- **Rota.** `ExigeCapacidade` protege a URL direta das páginas cuja leitura é configurável
  (templates, mensagens rápidas, programadas, lembretes, dashboard, automação): verificando →
  erro com "tentar novamente" → "Sem acesso" com volta para Atendimentos. O menu esconde os mesmos
  itens e, com permissão desconhecida, não cai mais na regra por papel.
- **Sem F5.** `ACESSO_ALTERADO` invalida `["permissoes"]`; cada aba tem a própria assinatura STOMP e
  revalida sozinha. O backend continua sendo a fonte final (POST direto com sessão antiga → 403).
- **Testes.** `vitest.setup.ts` controla `useCapacidades` (`src/test/capacidades-de-teste.ts`,
  padrão "tudo permitido"); testes de permissão declaram o cenário ou usam o hook real
  (`usarCapacidadesReais()`), e o E2E `permissoes-na-interface.spec.ts` roda contra o backend real.

### 14.1 Matriz auditada (capacidade → controle → ponto de entrada)

"Antes" é o que decidia o controle até esta correção.

| Capacidade | Controles na UI | Endpoint / caso de uso | Antes | Agora |
|---|---|---|---|---|
| `templates.ver` | menu; rota `/templates-whatsapp`; item "Templates" do clipe; "Nova mensagem" com janela fechada | `GET /whatsapp/templates` | menu ok; resto sem checagem | capacidade |
| `templates.criar` | "Novo template" (página); link "Criar template" (modal do composer) | `POST /whatsapp/templates` | `podeCriarTemplates(papel)` (todos) | capacidade |
| `templates.editar` / `templates.excluir` | lápis / lixeira (página e modal) | `PUT`/`DELETE /whatsapp/templates/{id}` | `podeGerenciarTemplates(papel)` | capacidade, cada uma separada |
| `mensagens_rapidas.usar` | menu; rota; item do clipe; sugestões do atalho `/` | `GET /mensagens-rapidas` | menu ok; atalho só sumia com erro 403 | capacidade |
| `mensagens_rapidas.criar` / `editar_excluir` | "Nova mensagem rápida"; lápis/lixeira | `POST`/`PUT`/`DELETE /mensagens-rapidas` | sem checagem | capacidade |
| `mensagens_programadas.ver` | menu; rota; seção do painel da conversa | `GET /mensagens-programadas` | menu ok | capacidade |
| `mensagens_programadas.criar` | "Programar mensagem"; relógio do composer; "Adicionar" no painel; botão na ficha da agenda | `POST /mensagens-programadas` | sem checagem | capacidade |
| `mensagens_programadas.editar_cancelar` | editar/cancelar (página e painel) | `PUT /{id}`, `POST /{id}/cancelar` | sem checagem | capacidade |
| `lembretes.ver` / `criar` / `editar_excluir` | menu; rota; seção do painel; "Novo lembrete", "Adicionar", botão da ficha; concluir, lápis, lixeira | `/lembretes` | menu ok; ações sem checagem | capacidade |
| `tags.criar` / `tags.editar_excluir` | "Nova tag"; lápis/lixeira em `/tags` | `POST`/`PUT`/`DELETE /tags` | **sem checagem (atendente via os botões)** | capacidade |
| `tags.aplicar` | seletor de tags do cabeçalho/painel; "⋯ Tags"; tags da ficha da agenda | `PUT`/`DELETE /leads/{id}/tags/{tag}` | sem checagem | capacidade (chips seguem visíveis) |
| `contatos.editar` | nome, código e notas no painel; formulário da ficha da agenda | `PUT /leads/{id}` | sem checagem | capacidade (vira leitura) |
| `resumo_ia.ver` / `resumo_ia.solicitar` | seção de resumo (painel e ficha); "Gerar/Regerar" | `GET`/`POST /atendimentos/{id}/resumo-ia` | sem checagem | capacidade |
| `atendimentos.responder` | composer; responder/encaminhar/reagir no menu da mensagem | `POST /atendimentos/mensagens` etc. | estado da conversa | capacidade **e** estado da conversa; negado → aviso no lugar do composer, reações só leitura |
| `atendimentos.iniciar_conversa` | botão de novo atendimento da lista; "Iniciar conversa" do cartão de contato | `POST /atendimentos/novo-contato` | sem checagem | capacidade |
| `atendimentos.abrir_para_contato` | "Reativar atendimento" (finalizado); "Abrir atendimento" na agenda e na ficha | `POST /atendimentos/leads/{id}/novo` | sem checagem | capacidade |
| `atendimentos.transferir` / `devolver_ia` | "Transferir"; opções do diálogo (assumir/colegas × devolver) | `POST /atendimentos/{id}/transferir` | sem checagem | capacidade, por opção |
| `atendimentos.finalizar` | "Finalizar" | `POST /atendimentos/{id}/finalizar` | sem checagem | capacidade |
| `atendimentos.finalizar_lote` | "Finalizar Todos" (⋯ da lista) e a contagem | `GET`/`POST /atendimentos/finalizar-lote` | sem checagem (a contagem dava 403) | capacidade |
| `atendimentos.colaborar` | pedir entrada, entrar, convidar, aceitar/recusar; "pedir entrada" da agenda | `pedir-entrada`, `entrar`, `convidar` | `papel !== "ATENDENTE"` | capacidade; "entrar direto" usa `alcancaTodos` |
| `dashboard.ver` | menu; rota `/dashboard` | `GET /dashboard/visao-geral` | papel + menu | capacidade |
| `automacao.ver` | menu; rota `/automacao` | `GET /automacao/**` | papel (dentro da página) | capacidade (rota) |
| `automacao.editar_parametros` | switches de recursos de IA; parâmetros avançados + salvar | `PUT /automacao/config/**` | sem checagem | capacidade (fieldset + switches) |
| `automacao.regras` | novo, alternar, excluir e editar regras de follow-up/fidelização | `/automacao/follow-ups*`, `/fidelizacao*` | sem checagem | capacidade |
| `equipe.ver` / `equipe.disponibilidade_ia` | card de disponibilidade na automação; switch | `GET /usuarios`; `PATCH .../disponibilidade-ia` | sem checagem | capacidade (switch inoperante, pois também mostra o estado) |
| `equipe.*`, `equipe.perfis`, `equipe.excecoes_atendentes` | aba Equipe e demais abas da Gestão | `/usuarios/**`, `/gestao/permissoes/**` | já por capacidade (docs/47 §9) | sem mudança |
| `atendimentos.ver` | abas Meus/Todos, filtro de atendente | Specification + RLS | papel (estrutural) | sem mudança (RN-CRM-01) |

**Fora da matriz, de propósito:**
- configuração de fidelização e datas festivas (G/D, fora do catálogo) e importação/exportação
  (`gerenciaImportacaoDeLeads`, §2.2) seguem por papel;
- colunas "atendente" e agrupamento por autor em lembretes, programadas e mensagens rápidas são
  só apresentação;
- a alçada da aba Equipe (quem administra quem) espelha a do backend, que continua decidindo.

### 14.2 Auditoria de Atendimentos e Resumo por IA (cadeia completa)

Cadeia conferida: efetivo de `/minhas` → controle na tela → `@PreAuthorize` do caso de uso →
revogação com a sessão aberta (`ACESSO_ALTERADO`, §7). A proteção contra regressão é
`CapacidadesDeAtendimentoIT` (parametrizado): para cada linha, revogar no perfil com o token já
emitido → 403 **e** estado intacto; perfil padrão → a atendente executa na própria conversa.

| Capacidade | Controle na UI | Endpoint / caso de uso | Teste positivo | Teste negativo | Lacuna encontrada |
|---|---|---|---|---|---|
| `atendimentos.transferir` | "Transferir" (cabeçalho/⋯); colegas e "Assumir para mim" no diálogo | `POST /atendimentos/{id}/transferir` com destino — `TransferirAtendimentoUseCase` | `CapacidadesDeAtendimentoIT`, `AtendimentoAcoesControllerIT.transferir_atendenteParaColegaAtivo_retorna200`, `dialogo-transferir.test` | IT: revogada → 403 sem mudar dono; colega → 404; Potencial para colega → 403; finalizado → 409; destino inativo/inexistente → 422 | **Diálogo oferecia colegas num Potencial (EM_IA) a quem não alcança todos: todo clique voltava 403 com o detalhe técnico.** Corrigido na tela (§14.2.1). |
| `atendimentos.devolver_ia` | "Devolver para a IA" no diálogo | mesmo endpoint, destino nulo | `CapacidadesDeAtendimentoIT` | revogada → 403, segue `EM_ATENDIMENTO` | Opção aparecia em conversa já `EM_IA` (no-op). Corrigido. |
| `atendimentos.finalizar` | "Finalizar" | `POST /atendimentos/{id}/finalizar` | `CapacidadesDeAtendimentoIT` | revogada → 403, segue `EM_ATENDIMENTO` | nenhuma |
| `resumo_ia.ver` | seção "Resumo por IA" no painel do chat e na ficha | `GET /atendimentos/{id}/resumo-ia`; `resumoIa` em `GET /leads/{id}` e `/leads/{id}/agenda` | `CapacidadesDeAtendimentoIT`, `painel-da-conversa.test`, `painel-lateral-lead.test` | revogada → 403 no estado e `resumoIa: null` na ficha; seção some no chat e na ficha | **A ficha não tinha teste**: o mock local nunca liberava a capacidade. Teste acrescentado. |
| `resumo_ia.solicitar` | "Gerar"/"Regerar" | `POST /atendimentos/{id}/resumo-ia` | `CapacidadesDeAtendimentoIT` (sem webhook no IT: 503 depois da autorização) | revogada → 403 e nenhuma solicitação criada; leitura continua (estado 200, ficha com texto) | nenhuma |

Fora desta etapa, registrado para uma próxima: `atendimentos.responder`, `iniciar_conversa`,
`abrir_para_contato`, `colaborar` e `finalizar_lote` têm controle e `@PreAuthorize` conferidos na
§14.1, mas ainda não entram na matriz parametrizada (exigem janela de 24 h, canal ou participantes
no fixture). Mesma proposta para Tags, Templates e Mensagens rápidas/programadas.

#### 14.2.1 Transferência — o que o relato "não consigo transferir" era

Reproduzido como atendente (backend e banco de dev, navegador): a capacidade estava permitida e o
`POST` da conversa própria respondia 200. As duas falhas visíveis eram da tela:

1. **Potencial (EM_IA):** o diálogo listava todos os colegas; qualquer clique → `403 Transferencia
   de potencial proibida` com o detalhe técnico na tela. A regra está certa (RN-CRM-01/02: atendente
   não escolhe destino de Potencial; gestão distribui). Agora quem não tem `alcancaTodos` vê só
   "Assumir para mim" e a explicação; a gestão segue vendo os colegas.
2. **Conversa própria:** a transferência funcionava, mas a perda de acesso que vem dela acendia em
   vermelho "Esta conversa não está mais disponível para você". Agora aparece "Atendimento
   transferido para {nome}."; revogação alheia continua com o aviso vermelho.

Recusas 403/404/409/422 viram textos do catálogo. A diferença entre a lista de destinos
(subgestor só quando ONLINE e disponível para a IA) e a validação da transferência explícita
(qualquer ATENDENTE/SUBGESTOR ativo) é intencional e está documentada em
`AtendenteParaTransferenciaRepositorioJdbc`; não explica o relato e não foi alterada.

### 14.3 Os três controles do Resumo por IA

| Controle | Onde | Efeito |
|---|---|---|
| `resumo_ia.ver` (Gestão) | perfil ou exceção | Sem ele: seção some no chat e na ficha; a API não devolve o texto (`resumoIa: null`) e o estado do ciclo responde 403. |
| `resumo_ia.solicitar` (Gestão) | perfil ou exceção; depende de `ver` | Sem ele: some "Gerar/Regerar" e o `POST` responde 403. **A leitura do resumo pronto continua** (regra do catálogo: são ações separadas). |
| "Resumo automático por IA" (Automação) | `configuracao_resumo_ia.ativo` | Liga/desliga a **geração automática** pelo gatilho do n8n. Não esconde a seção, não bloqueia leitura nem o botão manual. |

Mudar o terceiro para "esconde o recurso inteiro" é decisão de produto pendente (§13): o relato
não permitiu saber qual controle foi desligado.

## 15. Perfil Operador (decisões aprovadas em 04/10/2026)

`OPERADOR` é um papel operacional configurável próprio, exibido como **Operador**, não uma
herança de gestor/administrador. Gestor e administrador criam, editam, desativam e configuram
esses usuários pela Gestão. A alçada do subgestor continua limitada aos atendentes.

### 15.1 Tetos e padrões

- Atendimentos, contatos, tags, mensagens rápidas/templates, programadas e lembretes seguem os
  tetos operacionais do ATENDENTE. As ações continuam configuráveis no catálogo existente.
- `atendimentos.ver` tem alcance **MEUS**: mesmos leads próprios e potenciais da IA, sem visão
  global. RLS e Specification continuam decidindo; não há filtro de segurança só no frontend.
- `atendimentos.transferir` e as cinco capacidades de recebimento abaixo começam desligadas.
  Responder/finalizar/devolver à IA seguem os padrões operacionais já existentes.
- Não recebe Gestão, administração, dashboard global ou automação por herança. Capacidades
  fora do teto são recusadas com 422, inclusive após copiar um perfil mais privilegiado.
- Campanhas tem teto **VER**, padrão **SEM_ACESSO** e `campanhas.ver = false`. Só concessão
  explícita por Gestão **mais** feature flag `campanhas` habilitada libera lista, detalhe e
  métricas agregadas existentes. Não cria, edita, inicia, pausa, cancela, envia teste ou altera
  configurações. A flag desligada mantém 404; permissão ausente/revogada mantém 403.
- `campanhas.ver_destinatarios` separa destinatários, conferência, CSV, opt-outs, excluídos,
  prévia de público e projeção. Continua concedida por padrão à gestão atual, fora do teto do
  Operador: leitura de campanhas não pode expor a carteira de outros atendentes.

O Operador não entra no rodízio da IA: disponibilidade e `/internal/v1/atendentes/disponiveis`
permanecem exclusivos dos papéis ATENDENTE/SUBGESTOR. Presença e usuário ativo não substituem
nenhuma capacidade. Os usuários existentes não são convertidos nem recebem permissões novas.

### 15.2 Matriz de origem do destino humano

No perfil Operador ou em suas exceções, as ações configuráveis de Atendimentos são:

| Capacidade do destinatário | Origem permitida |
|---|---|
| `atendimentos.receber_de_atendente` | ATENDENTE |
| `atendimentos.receber_de_operador` | OPERADOR |
| `atendimentos.receber_de_subgestor` | SUBGESTOR |
| `atendimentos.receber_de_gestor` | GESTOR |
| `atendimentos.receber_de_administrador` | ADMINISTRADOR |

Todas começam OFF, exigem nível EDITAR e dependem de `atendimentos.responder`. A política usa
as permissões **efetivas do destinatário ativo** e o papel autenticado da origem, não dados
do payload. A mesma verificação filtra destinos e protege transferência/convite direto; a
permissão de origem não substitui `atendimentos.transferir`/`colaborar` do solicitante.
ATENDENTE e SUBGESTOR preservam suas regras anteriores. Destinos Operador elegíveis aparecem
em **Outros**, agrupados exclusivamente pelo campo `papel` da resposta.

Transferência para destino inativo/inexistente retorna 422; origem não concedida, destino
Operador igual ao ator ou redistribuição de Potencial por papel sem alcance global retorna
403. A exceção existente de assumir Potencial para si permanece; finalizado permanece 409,
atendimento inacessível permanece 404. Convite consentido mantém a propriedade do responsável
e não permite entrada direta do Operador em atendimento alheio.

### 15.3 Contratos existentes estendidos (sem endpoints paralelos)

- `POST /api/v1/usuarios` e `PUT /api/v1/usuarios/{id}` aceitam `papel: OPERADOR`, mantendo
  payload/retorno e autenticação JWT. Gestor/administrador têm alçada; subgestor não.
- `GET /api/v1/gestao/permissoes/perfis` inclui o quarto perfil, Operador.
- `PUT /api/v1/gestao/permissoes/perfis/OPERADOR` salva `niveis`/`acoes` no modelo atual;
  exceções e cópia usam as mesmas rotas existentes. Tetos/delegação continuam no backend.
- `GET /api/v1/atendimentos/destinos-de-transferencia` mantém `id`, `nome` e `papel` opcional,
  agora admitindo OPERADOR quando a origem foi concedida. Nenhuma alteração no contrato n8n.
- `POST /api/v1/atendimentos/{id}/transferir` e `/convidar` reutilizam os payloads atuais,
  com a matriz de recebimento adicionada à validação. Erros continuam RFC 7807.
- OpenAPI publica os enums e os erros de autorização/inelegibilidade sem relaxar acesso.

`V92` adiciona o enum em uma transação separada; `V93` amplia o CHECK/registro do perfil e
recria políticas de leitura/escrita de leads/atendimentos e leitura de resumo/convites com
o mesmo recorte do ATENDENTE. Campanhas ganha apenas SELECT sobre campanhas e contadores
agregados. Não amplia RLS de destinatários/opt-outs nem o acesso global da Agenda.

### 15.4 Limitações preservadas e validação

Participação consentida segue docs/51: permite histórico e resposta autorizada, mas o acesso
à ficha por `GET /leads/{id}` ainda tem a lacuna preexistente da Specification documentada lá.
Esta etapa não concede visão global para contorná-la. A Agenda continua sem alcance global
para Operador. Não houve envio externo, merge ou deploy.

Evidências automatizadas: `PoliticaDePermissoesTest`, `GestaoPermissoesIT` (criação/login,
isolamento, concessão/revogação, cópia, transferência e convite/aceite), `CampanhaApiIT`
(read-only, destinatários negados, flag e teto), `OpenApiIT`, testes de Gestão, Campanhas e
seletor de destinos. A validação headed usa backend e PostgreSQL locais, não respostas
mockadas no navegador. Procedimento operacional: docs/18.

Screenshots e procedimento headed: [docs/assets/perfil-operador](assets/perfil-operador/README.md).
A negativa de entrada direta sem alçada agora usa `AccessDeniedException`: mantém o bloqueio
existente e devolve 403 em vez do 500 preexistente, com teste para ATENDENTE e OPERADOR.
