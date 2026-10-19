# Validação local — Cancelamentos

## Continuação com persistência local — 19/10/2026

As capturas `cancelamentos-persistidos-*` substituem as capturas anteriores como evidência da
integração atual. Next.js real em localhost:3019, Spring Boot real em localhost:8099, PostgreSQL
15 e Redis descartáveis. **Sem FonteCancelamentosDeTeste/@Primary e sem interceptação de API no
navegador**: os registros desta rodada foram gravados por POST no endpoint interno implementado.
Flag ON apenas no banco descartável, login público do seed dev, provedor de mensagens fake.

| Captura | Verificação |
|---|---|
| cancelamentos-persistidos-1920.png | 1920×1080; dois registros recebidos pelo endpoint, datas reais distintas |
| cancelamentos-persistidos-1280.png | 1280×900; terceiro registro recebido sem F5, nomes longos contidos |
| cancelamentos-persistidos-filtro-origem-1280.png | Base CANCELAMENTO exclui os dois registros sem ocorrência conhecida |
| cancelamentos-persistidos-mobile-390.png | 390×844; filtros e navegação reais da aplicação |
| cancelamentos-persistidos-lista-390.png | Cartões com datas/ausências e ação acessível, sem tabela duplicada |
| cancelamentos-persistidos-detalhe-390.png | Modal, nome longo, ocorrência desconhecida explícita, sem vínculo inventado |

Em 1920, 1280 e 390, scrollWidth igual à viewport, devicePixelRatio 1; após document.fonts.ready,
fontes loaded e font-family computado `Inter, "Inter Fallback", Inter, sans-serif`. Fonte/tema do
produto preservados. A marca do seed é configuração de desenvolvimento, não regra por cliente.

Sequência observada: dois POSTs iniciais = 201; página com dois registros; terceiro POST externo
ao navegador = 201; novo contato surgiu na tela **sem reload**; após reload, os três continuaram
presentes. Não se executou workflow n8n real. Em seguida, teclado ArrowDown/End/Enter selecionou
Cancelamento na origem e Aplicar retornou somente o contato A (único com canceladoEm conhecido).
Novo reload restaurou período/base padrão e três registros persistidos. Escape fechou o detalhe,
devolvendo foco ao botão Ver detalhes. Comparação visual dos PNGs confirmou contenção de nomes,
botões e metadados nas larguras testadas.

Vínculo positivo e autorização negativa são provas automatizadas HTTP/PostgreSQL + frontend, não
cliques em conversa de cliente. O preview local não conecta Meta/Uzapi, não envia mensagens e não
é homologação Fêmina/n8n. Logo privada não provisionada usa fallback existente; o indicador de
dev do Next aparece em algumas capturas. Logs brutos de browser não são versionados: podem
conter token descartável no handshake. Falhas iniciais de startup/classpath do preview foram
descartadas, não apresentadas como evidência final. CI e clean verify estão no relatório da PR.

## Evidência histórica da etapa inicial — 18/10/2026

18/10/2026. Aplicação Next.js real em `localhost:3019`, Spring Boot real em `localhost:8099`,
PostgreSQL 15 e Redis descartáveis por Testcontainers. Login pelo formulário do CRM com o
administrador público do seed de desenvolvimento. Não são telas HTML reproduzidas nem dados reais
da Fêmina. A marca visível é a configuração padrão do ambiente dev, não regra da nova aba.

Dados vêm de `FonteCancelamentosDeTeste` (`crm-app/src/test`), injetada somente no ambiente local
de teste. Não há mock de dados no frontend de produção, consulta ao n8n ou contato com pacientes.
Flag habilitada apenas no banco descartável para exercitar a tela; V108 permanece OFF.

## Capturas finais

- `cancelamentos-desktop-1920.png`: 1920×1080, tabela com registro/agendamento distintos, campos ausentes.
- `cancelamentos-desktop-1280.png`: 1280×900, filtros e ações contidos na viewport.
- `cancelamentos-filtros-1280.png`: nome + profissional + tipo combinados, um resultado no servidor.
- `cancelamentos-mobile-390.png`: 390×844, formulário com componentes reais do design system.
- `cancelamentos-mobile-lista-390.png`: 390×844, cartões com todos os campos e ação visível.
- `cancelamentos-detalhe-mobile-390.png`: detalhe contido na viewport, descrição com quebra de linha,
  fechamento e aviso explícito de vínculo indisponível.

Antes das capturas finais: `await document.fonts.ready`, `document.fonts.status = loaded`;
font-family computado `Inter, "Inter Fallback", Inter, sans-serif`, escala do dispositivo 1.
`document.documentElement.scrollWidth` = viewport em 1920, 1280 e 390; nenhum overflow horizontal
da página. A primeira comparação mobile revelou tabela com rolagem horizontal e nomes quebrados:
foi substituída por cartões somente abaixo do breakpoint estreito já existente. Essa captura
intermediária não é evidência final.

Interações verificadas no navegador headed: abrir detalhe por Enter, fechar por Escape, foco
retorna ao botão Ver detalhes; combinar filtros textuais e limpar (três registros novamente);
abrir calendário existente e selecionar data. Nada foi enviado a provedor nem reprocessado.
Autorização negativa, datas/fuso, falhas, estados vazio/não configurado e concorrência de respostas
também são cobertos por testes automatizados, não apresentados como homologação externa.

Limites do preview: logo privado não provisionado (fallback local existente) e proxy WebSocket
não preparado neste servidor Next de teste. Não afirmar validação de tempo real com fornecedor;
a aba não implementa polling nem depende de WebSocket. Logs brutos do navegador não são
versionados (podem incluir token descartável de sessão na URL do WebSocket).

Resultados completos e CI do último SHA constam no relatório da PR #313. Capturas não comprovam
instalação do SQL externo, valores reais, correlação de lead ou operação na Fêmina.
