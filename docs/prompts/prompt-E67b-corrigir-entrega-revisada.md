# Prompt E67b — corrigir divergências da revisão da E67

> Leia AGENTS.md, docs/13-estado-do-projeto.md e docs/prompts/COMO-ESCREVER-PROMPTS.md antes de
> alterar qualquer arquivo.
>
> Esta é uma correção da E67. Trabalhe em worktree/branch isolada. Não faça push sem autorização
> explícita do Marcondes e não remova prompts não rastreados existentes.
>
> O relatório anterior não é evidência: confira o código e os testes. Ao terminar, rode npm run lint,
> npm run typecheck, npm test -- --run e npm run build. Como textos.json foi alterado, rode também
> cd backend && ./mvnw clean verify com Java 21 e Testcontainers. Se o clean falhar por arquivo
> bloqueado, informe o caminho e não declare o ciclo completo aprovado. Sem push, escreva CI não verificado.

---

## Divergências confirmadas

Os commits revisados foram 06aae4d, f1f5c45 e 4a180ab. A auditoria confirmou:

1. Não existe seção “Oportunidade” em PainelLateralLead, apesar de o relatório afirmar que ela foi
   adicionada.
2. O botão de telefone em frontend/src/components/leads/painel-lateral-lead.tsx renderiza Phone,
   mas não possui onClick nem href tel:. O fallback “Ligar” também está literal.
3. sidebar.tsx possui o estado novidadesAberto e importa Sparkles, mas não renderiza trigger para
   abrir NovidadesDialog. Portanto, o modal não é acessível pela sidebar.
4. novidades-dialog.tsx usa cores literais bg-amber-100, text-amber-700, bg-sky-100 e text-sky-700.
5. A aba Novidades não agrupa por data nem exibe selo NOVO estruturado como na referência.
6. Não há testes novos nos três commits para esses comportamentos.
7. Administração foi restringida a ADMINISTRADOR, enquanto o prompt E67 exigia preservar a fronteira
   de gestão GESTOR e ADMINISTRADOR, ou parar para decisão explícita.
8. /administracao apenas renderiza Placeholder; ocultar o link não protege acesso direto.
9. npm run lint passou com 0 erros e 4 warnings, incluindo Sparkles não utilizado.
10. typecheck, build e frontend (45 arquivos/179 testes) passaram. mvnw clean verify falhou no clean
    ao apagar backend/crm-app/target/crm-app-0.1.0-SNAPSHOT-exec.jar; mvnw verify -DskipTests
    compilou, mas não substitui o ciclo completo.

Confirme se a base não mudou e corrija os fatos acima; não os repita como “já feito”.

---

## Bloco 1 — Novidades & Em Breve deve abrir de verdade

Em frontend/src/components/shell/sidebar.tsx:

- Renderize um botão real “Novidades & Em Breve” antes do rodapé do usuário, na ordem da referência.
- O botão deve chamar setNovidadesAberto(true) em um clique completo.
- Use Sparkles; sidebar expandida mostra o rótulo do catálogo, retraída mostra o ícone com
  aria-label e tooltip.
- Use Button ou o padrão acessível de menu; não use div clicável.
- O item não pode desaparecer por erro/carregamento de features.
- Não o coloque dentro do menu de presença.

Em novidades-dialog.tsx:

- Preserve textos e itens no textos.json e TextosSchema; não crie arrays no componente.
- Use Dialog acessível, fechamento padrão, Escape, foco visível e abas com role/aria-selected
  coerentes, quando implementadas como tabs.
- Agrupe itens da aba Novidades por data real em ordem decrescente, mostrando a data uma vez por
  grupo. Não use a posição no array para decidir o que é novo.
- Se o catálogo ainda não tiver estado/flag de novidade, acrescente campo estruturado ao JSON/schema
  e marque NOVO somente onde configurado.
- Formate a data de forma determinística e local; não copie datas/valores da screenshot como dados.
- Troque as cores bg-amber-100/text-amber-700/bg-sky-100/text-sky-700 por Badge, PillDeStatus
  ou tokens semânticos já existentes. Nenhuma cor literal nova.
- Mapeie ícones por tabela segura de chaves; strings do catálogo não podem ser executadas como JSX.
- Use chaves estáveis, mantenha cards responsivos, scroll interno e ausência de overflow horizontal.

---

## Bloco 2 — ficha sem mock e telefone funcional

Não crie uma seção Oportunidade com valor mockado: LeadFicha não fornece esse dado. Remova qualquer
valor fictício, R$, percentual, nome ou etapa copiado da imagem. Não crie migration, coluna, endpoint
ou campo customizado para preencher essa lacuna.

No PainelLateralLead:

- Quando houver telefone real, o controle deve abrir destino tel: seguido do número real, usando
  o padrão de link/botão já existente.
- aria-label, title e tooltip vêm do catálogo. Remova o fallback literal “Ligar”.
- Sem telefone, não renderize controle sem ação.
- Preserve Abrir atendimento, callback da Agenda, tags, notas, timeline, lembretes e mensagens
  programadas.
- O X de fechar continua neutro, não destructive.

Adicione teste que falhe se telefone existir sem destino funcional e que confirme ausência do botão
quando telefone for nulo.

---

## Bloco 3 — Administração e autorização

Sem decisão explícita do Marcondes, restaure a fronteira de gestão exigida pelo prompt:

- GESTOR e ADMINISTRADOR veem Administração;
- ATENDENTE não vê;
- acesso direto a /administracao deve respeitar o guard existente, não apenas a ocultação visual;
- não crie CRUD, endpoint, migration ou permissão backend para Placeholder;
- se não houver guard reutilizável no App Router, use a menor proteção coerente com auth atual e
  registre que a proteção definitiva será backend quando surgirem operações reais;
- a tela continua estado futuro sem dados sensíveis.

Teste positivamente GESTOR/ADMINISTRADOR e negativamente ATENDENTE no menu e acesso direto. Não use
nome do usuário para simular papel. Se o Marcondes decidir explicitamente “somente ADMINISTRADOR”,
registre a decisão e teste também o negativo para GESTOR; até essa decisão, não mantenha a restrição
silenciosa criada pelo relatório.

---

## Bloco 4 — destructive sem regressão

Preserve os estilos destructive de composer.tsx e painel-da-conversa.tsx:

- excluir/remover/descartar usa tokens destructive;
- fechar painel/modal e limpar campo não ficam vermelhos por serem X;
- icon-only tem aria-label e foco visível;
- não adicione cores literais;
- não altere envio, áudio, upload, tags, lembretes ou mensagens programadas.

---

## Testes e validação

Crie/atualize testes de componente para provar:

- trigger de Novidades abre modal em sidebar expandida e retraída;
- modal fecha, alterna abas e expõe estado selecionado acessível;
- novidades agrupam por data e mostram NOVO somente quando configurado;
- cards Em breve usam status/previsão do catálogo e tokens;
- GESTOR/ADMINISTRADOR veem Administração, ATENDENTE não vê;
- acesso direto à Administração respeita o guard disponível;
- ficha não exibe oportunidade mockada;
- telefone real gera destino tel: e telefone ausente não mostra botão;
- destructive continua destrutivo sem pintar o fechamento;
- Agenda, atendimento, tags, lembretes e mensagens programadas não regrediram.

Use userEvent e renderização real. Para assíncrono use waitFor/utilidade equivalente; não use
Thread.sleep, timeout cego ou asserção imediata.

Faça screenshots ou validação no navegador, registrando viewport, de:

1. sidebar expandida com Novidades e Administração;
2. sidebar retraída;
3. modal em Novidades com agrupamento/selo;
4. modal em Em breve;
5. Administração com papel autorizado;
6. ficha com telefone;
7. ficha sem telefone e sem oportunidade.

---

## Definição de pronto

- [ ] Novidades existe na sidebar e abre o modal por clique normal.
- [ ] Modal acessível, agrupado, responsivo e alimentado somente pelo catálogo.
- [ ] Nenhuma cor literal nova.
- [ ] Nenhuma oportunidade mockada.
- [ ] Telefone usa o número real em tel: e não tem fallback literal.
- [ ] Administração respeita GESTOR/ADMINISTRADOR e acesso direto não ignora o guard.
- [ ] Testes positivos e negativos foram adicionados para cada ponto.
- [ ] lint, typecheck, testes frontend e build passam; warnings são relatados corretamente.
- [ ] clean verify backend foi executado e seu resultado real foi informado.
- [ ] Relatório informa SHA-base/final, commits, arquivos, decisões, divergências, bugs, fora de
      escopo, screenshots, CI e ação necessária no Dokploy. Expectativa: nenhuma variável nova;
      se surgir, atualizar .env.example e README.md.
- [ ] Nenhum commit/push sem autorização explícita.

## Fora de escopo

- Não implementar CRUD da Administração.
- Não criar banco, migration ou endpoint de novidades.
- Não expor dados pessoais nem relaxar autorização.
- Não misturar E65, E66 ou E68.
