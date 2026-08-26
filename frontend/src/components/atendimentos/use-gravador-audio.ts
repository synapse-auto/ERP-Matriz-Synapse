"use client";

import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";

import type { ConfiguracaoComposer } from "@/lib/atendimento/types";

const FORMATOS_ACEITOS_PELA_META = [
  "audio/mp4;codecs=mp4a.40.2",
  "audio/mp4",
] as const;
const semAssinatura = () => () => {};
const semFormatoNoServidor = () => null;

export type ErroDeGravacao =
  | "SEM_MICROFONE"
  | "PERMISSAO"
  | "EM_USO"
  | "CAPTURA"
  | "TAMANHO"
  | null;
export type FaseDaGravacao = "INATIVO" | "GRAVANDO" | "PREVISUALIZACAO";

export function formatoDeGravacaoDisponivel(): string | null {
  if (
    typeof window === "undefined" ||
    !window.isSecureContext ||
    typeof MediaRecorder === "undefined" ||
    !navigator.mediaDevices?.getUserMedia
  ) {
    return null;
  }
  return (
    FORMATOS_ACEITOS_PELA_META.find((tipo) =>
      MediaRecorder.isTypeSupported(tipo),
    ) ?? null
  );
}

export function useGravadorAudio(
  configuracao: ConfiguracaoComposer | undefined,
) {
  const mimeType = useSyncExternalStore(
    semAssinatura,
    formatoDeGravacaoDisponivel,
    semFormatoNoServidor,
  );
  const [fase, setFase] = useState<FaseDaGravacao>("INATIVO");
  const [segundos, setSegundos] = useState(0);
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [erro, setErro] = useState<ErroDeGravacao>(null);
  const [limiteAtingido, setLimiteAtingido] = useState(false);

  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const inicioRef = useRef(0);
  const partesRef = useRef<Blob[]>([]);
  const descartarAoPararRef = useRef(false);
  const previewUrlRef = useRef<string | null>(null);

  const limparRecursos = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    recorderRef.current = null;
  }, []);

  const revogarPreview = useCallback(() => {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = null;
    }
    setPreviewUrl(null);
    setArquivo(null);
  }, []);

  useEffect(() => {
    return () => {
      descartarAoPararRef.current = true;
      if (recorderRef.current?.state === "recording")
        recorderRef.current.stop();
      limparRecursos();
      if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
    };
  }, [limparRecursos]);

  const parar = useCallback(() => {
    if (recorderRef.current?.state === "recording") recorderRef.current.stop();
  }, []);

  const iniciar = useCallback(async () => {
    if (!configuracao || !mimeType) return;
    revogarPreview();
    setErro(null);
    setLimiteAtingido(false);
    setSegundos(0);
    descartarAoPararRef.current = false;
    partesRef.current = [];

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream, { mimeType });
      streamRef.current = stream;
      recorderRef.current = recorder;

      recorder.ondataavailable = (evento) => {
        if (evento.data.size > 0) partesRef.current.push(evento.data);
      };
      recorder.onerror = () => {
        setErro("CAPTURA");
        descartarAoPararRef.current = true;
        limparRecursos();
        setFase("INATIVO");
      };
      recorder.onstop = () => {
        limparRecursos();
        if (descartarAoPararRef.current) {
          setFase("INATIVO");
          return;
        }
        // A validação do contêiner pertence ao backend: alguns navegadores declaram
        // video/quicktime mesmo quando os bytes contêm somente áudio. Não descarte a
        // gravação apenas pelo MIME informado pelo MediaRecorder.
        if (partesRef.current.length === 0 || partesRef.current.every((parte) => parte.size === 0)) {
          setErro("CAPTURA");
          setFase("INATIVO");
          return;
        }
        const blob = new Blob(partesRef.current, {
          type: recorder.mimeType || mimeType,
        });
        const gravacao = new File(
          [blob],
          `gravacao-${Date.now()}.m4a`,
          {
            type: blob.type,
          },
        );
        const url = URL.createObjectURL(gravacao);
        previewUrlRef.current = url;
        setArquivo(gravacao);
        setPreviewUrl(url);
        setErro(
          gravacao.size > configuracao.tamanhoMaximoAudioBytes
            ? "TAMANHO"
            : null,
        );
        setFase("PREVISUALIZACAO");
      };

      inicioRef.current = Date.now();
      recorder.start();
      setFase("GRAVANDO");
      intervalRef.current = setInterval(() => {
        const decorrido = Math.floor((Date.now() - inicioRef.current) / 1000);
        setSegundos(
          Math.min(decorrido, configuracao.duracaoMaximaAudioSegundos),
        );
        if (decorrido >= configuracao.duracaoMaximaAudioSegundos) {
          setLimiteAtingido(true);
          parar();
        }
      }, 250);
    } catch (falha) {
      limparRecursos();
      setFase("INATIVO");
      const nome = falha instanceof DOMException ? falha.name : null;
      setErro(
        nome === "NotFoundError"
          ? "SEM_MICROFONE"
          : nome === "NotAllowedError" || nome === "SecurityError"
            ? "PERMISSAO"
            : nome === "NotReadableError"
              ? "EM_USO"
              : "CAPTURA",
      );
    }
  }, [configuracao, limparRecursos, mimeType, parar, revogarPreview]);

  const descartar = useCallback(() => {
    setErro(null);
    setLimiteAtingido(false);
    setSegundos(0);
    if (fase === "GRAVANDO") {
      descartarAoPararRef.current = true;
      parar();
    } else {
      revogarPreview();
      setFase("INATIVO");
    }
  }, [fase, parar, revogarPreview]);

  return {
    disponivel: Boolean(configuracao && mimeType),
    mimeType,
    fase,
    segundos,
    arquivo,
    previewUrl,
    erro,
    limiteAtingido,
    iniciar,
    parar,
    descartar,
  };
}
