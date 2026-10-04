# Evidências visuais — perfil Operador

Capturadas em 04/10/2026 com Playwright CLI **headed**, em 1440×1000 e 800×900.
Aplicação e backend locais reais; PostgreSQL isolado `operador_visual`, migrations até V93.
Usuários de desenvolvimento e contato sintético. Nenhuma resposta HTTP foi interceptada ou
mockada no navegador; nenhuma mensagem externa foi enviada.

## Fluxo exercitado

1. Gestor criou o usuário pelo formulário da Gestão, selecionando Operador.
2. Configurou o perfil existente: recebimento de Gestor e Campanhas em nível Ver; confirmou
   a alteração sensível e recarregou para conferir persistência. Transferência de saída ficou OFF.
3. Abriu um atendimento local sem mensagem inicial, encontrou o Operador em Outros e
   selecionou-o. A consulta posterior ao PostgreSQL confirmou o responsável com papel OPERADOR.
4. O novo usuário fez login e trocou a senha provisória pelo fluxo real. Sua lista exibiu o
   atendimento próprio; abriu conversa e ficha. Não apareceu filtro global de atendentes.
5. Consultou Campanhas com a concessão e flag locais habilitadas: leitura do estado vazio real,
   indicadores reais zerados e criação desabilitada. Acesso direto à Gestão foi negado.

## Capturas

- [Criação](operador-criacao.png)
- [Permissões — desktop](operador-permissoes-desktop.png)
- [Teto de Campanhas — 800×900](operador-permissoes-800.png)
- [Destino em Outros — desktop](operador-destino-desktop.png)
- [Destino em Outros — 800×900](operador-destino-800.png)
- [Atendimento próprio — desktop](operador-atendimento-desktop.png)
- [Atendimento próprio — 800×900](operador-atendimento-800.png)
- [Gestão negada](operador-gestao-negada.png)

Os negativos de carteira alheia, transferência direta, auto-transferência, destinatários de
campanhas, mutações e revogação são comprovados por HTTP em `GestaoPermissoesIT` e
`CampanhaApiIT`, não apenas pela aparência das telas.

## Limites desta validação

Ambiente local sem credenciais de canal/automação: chamadas preexistentes de templates/canal
ficam indisponíveis; não foi validado envio externo. Não havia campanhas reais cadastradas
nesse banco; leitura de detalhe/métricas com campanha persistida foi coberta no Testcontainers.
Não foram usadas contas de produção nem foi realizado deploy.

Uma tentativa de entrada direta sem alçada revelou retorno 500 preexistente; a exceção foi
corrigida para 403 e revalidada pelos endpoints para Atendente e Operador.
