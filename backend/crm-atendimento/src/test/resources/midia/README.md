# Midias sinteticas

`video-validacao.mp4` reutiliza a fixture sintetica ja existente em crm-app/chat-interno
(7.905 bytes); nao e arquivo de cliente.

`audio-sintetico.ogg` e silencio Opus mono: OpusHead v1/48 kHz, preskip 312,
OpusTags sem dados pessoais e 50 pacotes de silencio de 20 ms, paginas Ogg com CRC.
Nao ha voz ou gravacao de cliente. Copia identica em crm-app/z-api para exercitar
HTTP → worker → storage privado → download autorizado.

Formato do container: https://www.rfc-editor.org/rfc/rfc7845
Essas fixtures nao comprovam codecs nem comportamento da conta real do fornecedor.

`documento-sintetico.pdf` e PDF 1.4 de uma pagina, texto sintetico, fonte Helvetica
padrao e tabela xref completa. Nao contem documento de cliente nem recursos externos.
