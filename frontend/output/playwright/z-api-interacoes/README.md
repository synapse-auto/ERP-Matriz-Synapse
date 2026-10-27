# Evidência local — respostas interativas Z-API

## Captura comprovada

[desktop.png](./desktop.png): tela real de Atendimentos, Next.js dev, backend Spring Boot Java 21,
PostgreSQL 15 descartável `zapi_qa`. Somente dados sintéticos; nenhuma chamada à conta
Z-API/n8n, mensagem real ou acesso produtivo. Captura em 26/10/2026, 1920×1080, zoom padrão.

Antes da captura foi aguardado `document.fonts.ready`; o navegador retornou:

```json
{"fonte":"Inter, \"Inter Fallback\", Inter, sans-serif","fontes":"loaded","largura":1920,"altura":1080,"botao":true,"lista":true}
```

A imagem comprova texto de botão/lista no histórico e snapshot da citação recebida.
Foi observado também, na mesma aplicação real, que a origem fora da página não estava
no DOM e passou a estar após clicar em “Ir para mensagem respondida” (GET autorizado
existente). O histórico sintético tinha mais de uma página. Nenhuma réplica HTML foi usada.

## Limites da evidência

- Não é homologação da conta Z-API, entrega ao WhatsApp ou execução do workflow.
- A sessão de validação foi interrompida junto com o ambiente local. O build posterior
  passou, mas iniciar o frontend para nova captura foi recusado pelo ambiente. Não foi
  tentado contornar a restrição. Não há captura final em viewport menor nem validação
  manual completa de reconexão/cancelamento com anexos nesta rodada.
- Recarga, dedup, emissão de evento e mesclagem de WebSocket têm cobertura automatizada;
  não chamar isso de prova visual de todos os cenários. O backend simulado só representa
  os transportes externos nos ITs; na captura o backend/consulta eram reais.
- O marcador de issues pertence ao Next dev. Houve avisos de chave repetida na inbox;
  a consulta real retornou um cartão e a captura mostra um cartão. Não foi estabelecida
  a causa nem concluída reprodução limpa em build de produção. Não declarar o aviso
  resolvido ou atribuí-lo a esta alteração sem evidência. A inbox não foi modificada.

## Repetição necessária pelo operador

Em ambiente sintético local autorizado: abrir Atendimentos, receber botão/lista com IDs
próprios distintos, repetir o mesmo callback, conferir uma mensagem por ID, recarregar,
navegar para origem fora da página, usar Responder/cancelar preservando texto e anexos,
reconectar e repetir em tela menor. Não usar pacientes nem replay de produção.
