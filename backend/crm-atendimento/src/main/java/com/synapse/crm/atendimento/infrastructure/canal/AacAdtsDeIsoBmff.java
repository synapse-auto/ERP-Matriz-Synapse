package com.synapse.crm.atendimento.infrastructure.canal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * O {@code MediaRecorder} do Chrome grava AAC em MP4 fragmentado ({@code moof}+{@code mdat}). A
 * Meta classifica esse container como {@code application/octet-stream} (erro 131053) e o cliente
 * nunca recebe o áudio, embora o {@code POST /messages} tenha devolvido 200.
 *
 * <p>Aqui extraímos os frames AAC e embrulhamos em ADTS — {@code audio/aac}, que a Cloud API
 * aceita. Se o arquivo não for fragmentado ou a extração falhar, devolvemos vazio e o adaptador
 * envia os bytes originais.
 */
final class AacAdtsDeIsoBmff {

    private static final int CABECALHO = 8;
    private static final int TRUN_DATA_OFFSET = 0x000001;
    private static final int TRUN_SAMPLE_SIZE = 0x000200;
    private static final int TRUN_SAMPLE_DURATION = 0x000100;
    private static final int TRUN_SAMPLE_FLAGS = 0x000400;
    private static final int TRUN_SAMPLE_CTO = 0x000800;
    private static final int TFHD_DEFAULT_SAMPLE_SIZE = 0x000010;
    private static final int TFHD_DEFAULT_BASE_IS_MOOF = 0x020000;

    private AacAdtsDeIsoBmff() {}

    static Optional<byte[]> extrairSeFragmentado(byte[] bytes) {
        if (bytes == null || bytes.length < CABECALHO || !contemMoof(bytes)) {
            return Optional.empty();
        }
        try {
            ConfigAac config = procurarConfig(bytes);
            if (config == null) {
                return Optional.empty();
            }
            List<byte[]> frames = extrairFrames(bytes);
            if (frames.isEmpty()) {
                return Optional.empty();
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            for (byte[] frame : frames) {
                saida.writeBytes(cabecalhoAdts(frame.length, config));
                saida.writeBytes(frame);
            }
            return Optional.of(saida.toByteArray());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static boolean contemMoof(byte[] bytes) {
        return caixaTopo(bytes, "moof") != null;
    }

    private static ConfigAac procurarConfig(byte[] bytes) {
        Caixa moov = caixaTopo(bytes, "moov");
        if (moov == null) {
            return null;
        }
        for (int i = moov.dados; i + 8 <= moov.fim; i++) {
            if (tipoEm(bytes, i + 4).equals("esds")) {
                long tamanho = inteiro32(bytes, i);
                if (tamanho < 8 || i + tamanho > moov.fim) {
                    continue;
                }
                ConfigAac config = lerEsds(bytes, i + CABECALHO, (int) (i + tamanho));
                if (config != null) {
                    return config;
                }
            }
        }
        return null;
    }

    private static ConfigAac lerEsds(byte[] bytes, int inicio, int fim) {
        if (fim - inicio < 4) {
            return null;
        }
        return lerDescritores(bytes, inicio + 4, fim);
    }

    private static ConfigAac lerDescritores(byte[] bytes, int posicao, int fim) {
        while (posicao < fim) {
            int tag = bytes[posicao] & 0xFF;
            posicao++;
            int[] lidos = {0};
            int tamanho = tamanhoDescriptor(bytes, posicao, fim, lidos);
            posicao += lidos[0];
            if (tamanho < 0 || posicao + tamanho > fim) {
                return null;
            }
            if (tag == 0x03) {
                ConfigAac dentro = lerEsDescriptor(bytes, posicao, posicao + tamanho);
                if (dentro != null) {
                    return dentro;
                }
            } else if (tag == 0x04) {
                ConfigAac dentro = lerDecoderConfig(bytes, posicao, posicao + tamanho);
                if (dentro != null) {
                    return dentro;
                }
            } else if (tag == 0x05 && tamanho >= 2) {
                return configDeAsc(bytes, posicao);
            }
            posicao += tamanho;
        }
        return null;
    }

    private static ConfigAac lerEsDescriptor(byte[] bytes, int inicio, int fim) {
        if (fim - inicio < 3) {
            return null;
        }
        int posicao = inicio + 2;
        int flags = bytes[posicao] & 0xFF;
        posicao++;
        if ((flags & 0x80) != 0) {
            posicao += 2;
        }
        if ((flags & 0x40) != 0) {
            if (posicao >= fim) {
                return null;
            }
            int url = bytes[posicao] & 0xFF;
            posicao += 1 + url;
        }
        if ((flags & 0x20) != 0) {
            posicao += 2;
        }
        if (posicao > fim) {
            return null;
        }
        return lerDescritores(bytes, posicao, fim);
    }

    private static ConfigAac lerDecoderConfig(byte[] bytes, int inicio, int fim) {
        if (fim - inicio < 13) {
            return null;
        }
        return lerDescritores(bytes, inicio + 13, fim);
    }

    private static ConfigAac configDeAsc(byte[] bytes, int inicio) {
        int b0 = bytes[inicio] & 0xFF;
        int b1 = bytes[inicio + 1] & 0xFF;
        int objectType = b0 >> 3;
        int freqIndex = ((b0 & 0x07) << 1) | (b1 >> 7);
        int canais = (b1 >> 3) & 0x0F;
        if (objectType == 0 || freqIndex > 12 || canais == 0) {
            return null;
        }
        int perfilAdts = Math.max(0, objectType - 1);
        return new ConfigAac(perfilAdts, freqIndex, canais);
    }

    private static List<byte[]> extrairFrames(byte[] bytes) {
        List<byte[]> frames = new ArrayList<>();
        int posicao = 0;
        while (posicao + CABECALHO <= bytes.length) {
            Caixa caixa = lerCaixa(bytes, posicao, bytes.length);
            if (caixa == null) {
                break;
            }
            if ("moof".equals(caixa.tipo)) {
                Fragmento fragmento = lerMoof(bytes, caixa);
                Caixa mdat = proximaMdat(bytes, caixa.fim);
                if (fragmento != null && mdat != null) {
                    int base = fragmento.dataOffsetPresente
                            ? caixa.inicio + fragmento.dataOffset
                            : mdat.dados;
                    int cursor = base;
                    for (int tamanho : fragmento.tamanhos) {
                        if (tamanho < 0 || cursor < 0 || cursor + tamanho > bytes.length) {
                            return List.of();
                        }
                        byte[] frame = new byte[tamanho];
                        System.arraycopy(bytes, cursor, frame, 0, tamanho);
                        frames.add(frame);
                        cursor += tamanho;
                    }
                    posicao = mdat.fim;
                    continue;
                }
            }
            posicao = caixa.fim;
        }
        return frames;
    }

    private static Fragmento lerMoof(byte[] bytes, Caixa moof) {
        Fragmento encontrado = null;
        int posicao = moof.dados;
        while (posicao + CABECALHO <= moof.fim) {
            Caixa caixa = lerCaixa(bytes, posicao, moof.fim);
            if (caixa == null) {
                break;
            }
            if ("traf".equals(caixa.tipo)) {
                Fragmento traf = lerTraf(bytes, caixa);
                if (traf != null) {
                    encontrado = traf;
                }
            }
            posicao = caixa.fim;
        }
        return encontrado;
    }

    private static Fragmento lerTraf(byte[] bytes, Caixa traf) {
        int defaultSampleSize = 0;
        boolean baseEhMoof = false;
        Fragmento trun = null;
        int posicao = traf.dados;
        while (posicao + CABECALHO <= traf.fim) {
            Caixa caixa = lerCaixa(bytes, posicao, traf.fim);
            if (caixa == null) {
                break;
            }
            if ("tfhd".equals(caixa.tipo) && caixa.fim - caixa.dados >= 8) {
                int flags = inteiro24(bytes, caixa.dados + 1);
                int cursor = caixa.dados + 8;
                if ((flags & 0x000001) != 0) {
                    cursor += 8;
                }
                if ((flags & 0x000002) != 0) {
                    cursor += 4;
                }
                if ((flags & 0x000008) != 0) {
                    cursor += 4;
                }
                if ((flags & TFHD_DEFAULT_SAMPLE_SIZE) != 0 && cursor + 4 <= caixa.fim) {
                    defaultSampleSize = (int) inteiro32(bytes, cursor);
                }
                baseEhMoof = (flags & TFHD_DEFAULT_BASE_IS_MOOF) != 0;
            }
            if ("trun".equals(caixa.tipo)) {
                trun = lerTrun(bytes, caixa, defaultSampleSize);
            }
            posicao = caixa.fim;
        }
        if (trun == null) {
            return null;
        }
        return new Fragmento(trun.tamanhos, trun.dataOffset, trun.dataOffsetPresente, baseEhMoof);
    }

    private static Fragmento lerTrun(byte[] bytes, Caixa trun, int defaultSampleSize) {
        if (trun.fim - trun.dados < 8) {
            return null;
        }
        int flags = inteiro24(bytes, trun.dados + 1);
        int sampleCount = (int) inteiro32(bytes, trun.dados + 4);
        if (sampleCount < 0 || sampleCount > 1_000_000) {
            return null;
        }
        int cursor = trun.dados + 8;
        boolean dataOffsetPresente = (flags & TRUN_DATA_OFFSET) != 0;
        int dataOffset = 0;
        if (dataOffsetPresente) {
            if (cursor + 4 > trun.fim) {
                return null;
            }
            dataOffset = (int) inteiro32(bytes, cursor);
            cursor += 4;
        }
        if ((flags & 0x000004) != 0) {
            cursor += 4;
        }
        int[] tamanhos = new int[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            if ((flags & TRUN_SAMPLE_DURATION) != 0) {
                cursor += 4;
            }
            if ((flags & TRUN_SAMPLE_SIZE) != 0) {
                if (cursor + 4 > trun.fim) {
                    return null;
                }
                tamanhos[i] = (int) inteiro32(bytes, cursor);
                cursor += 4;
            } else {
                tamanhos[i] = defaultSampleSize;
            }
            if ((flags & TRUN_SAMPLE_FLAGS) != 0) {
                cursor += 4;
            }
            if ((flags & TRUN_SAMPLE_CTO) != 0) {
                cursor += 4;
            }
            if (tamanhos[i] <= 0) {
                return null;
            }
        }
        return new Fragmento(tamanhos, dataOffset, dataOffsetPresente, false);
    }

    private static Caixa proximaMdat(byte[] bytes, int inicio) {
        int posicao = inicio;
        while (posicao + CABECALHO <= bytes.length) {
            Caixa caixa = lerCaixa(bytes, posicao, bytes.length);
            if (caixa == null) {
                return null;
            }
            if ("mdat".equals(caixa.tipo)) {
                return caixa;
            }
            posicao = caixa.fim;
        }
        return null;
    }

    private static Caixa caixaTopo(byte[] bytes, String tipo) {
        int posicao = 0;
        while (posicao + CABECALHO <= bytes.length) {
            Caixa caixa = lerCaixa(bytes, posicao, bytes.length);
            if (caixa == null) {
                return null;
            }
            if (tipo.equals(caixa.tipo)) {
                return caixa;
            }
            posicao = caixa.fim;
        }
        return null;
    }

    private static Caixa lerCaixa(byte[] bytes, int inicio, int limite) {
        if (inicio < 0 || limite - inicio < CABECALHO) {
            return null;
        }
        long tamanho = inteiro32(bytes, inicio);
        int dados = inicio + CABECALHO;
        if (tamanho == 1) {
            if (limite - dados < 8) {
                return null;
            }
            tamanho = inteiro64(bytes, dados);
            dados += 8;
        } else if (tamanho == 0) {
            tamanho = limite - inicio;
        }
        if (tamanho < dados - inicio || tamanho > limite - inicio) {
            return null;
        }
        return new Caixa(inicio, dados, inicio + (int) tamanho, tipoEm(bytes, inicio + 4));
    }

    private static byte[] cabecalhoAdts(int tamanhoFrame, ConfigAac config) {
        int tamanhoAdts = 7 + tamanhoFrame;
        byte[] cabecalho = new byte[7];
        cabecalho[0] = (byte) 0xFF;
        cabecalho[1] = (byte) 0xF1;
        cabecalho[2] = (byte) (((config.perfil & 0x03) << 6)
                | ((config.freqIndex & 0x0F) << 2)
                | ((config.canais >> 2) & 0x01));
        cabecalho[3] = (byte) (((config.canais & 0x03) << 6) | ((tamanhoAdts >> 11) & 0x03));
        cabecalho[4] = (byte) ((tamanhoAdts >> 3) & 0xFF);
        cabecalho[5] = (byte) (((tamanhoAdts & 0x07) << 5) | 0x1F);
        cabecalho[6] = (byte) 0xFC;
        return cabecalho;
    }

    private static int tamanhoDescriptor(byte[] bytes, int inicio, int fim, int[] lidos) {
        int posicao = inicio;
        int tamanho = 0;
        int passos = 0;
        while (posicao < fim && passos < 4) {
            int b = bytes[posicao] & 0xFF;
            posicao++;
            passos++;
            tamanho = (tamanho << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) {
                lidos[0] = passos;
                return tamanho;
            }
        }
        lidos[0] = passos;
        return -1;
    }

    private static String tipoEm(byte[] bytes, int posicao) {
        if (posicao < 0 || posicao + 4 > bytes.length) {
            return "";
        }
        return new String(bytes, posicao, 4, StandardCharsets.US_ASCII);
    }

    private static long inteiro32(byte[] bytes, int posicao) {
        return ((long) (bytes[posicao] & 0xFF) << 24)
                | ((long) (bytes[posicao + 1] & 0xFF) << 16)
                | ((long) (bytes[posicao + 2] & 0xFF) << 8)
                | (bytes[posicao + 3] & 0xFFL);
    }

    private static int inteiro24(byte[] bytes, int posicao) {
        return ((bytes[posicao] & 0xFF) << 16)
                | ((bytes[posicao + 1] & 0xFF) << 8)
                | (bytes[posicao + 2] & 0xFF);
    }

    private static long inteiro64(byte[] bytes, int posicao) {
        long valor = 0;
        for (int i = 0; i < 8; i++) {
            valor = (valor << 8) | (bytes[posicao + i] & 0xFFL);
        }
        return valor;
    }

    private record ConfigAac(int perfil, int freqIndex, int canais) {}

    private record Caixa(int inicio, int dados, int fim, String tipo) {}

    private record Fragmento(int[] tamanhos, int dataOffset, boolean dataOffsetPresente, boolean baseEhMoof) {}
}
