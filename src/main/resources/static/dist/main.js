const PLAYBACK_SAMPLE_RATE = 24000;
const PLAYBACK_LEAD_SECONDS = 0.12;
const PCM_FADE_SAMPLES = 128;
const PCM_MIN_BUFFER_BYTES = 16384;
let ws = null;
let audioCtx = null;
let mediaStream = null;
let sourceNode = null;
let processorNode = null;
let playbackCtx = null;
let playbackCursorTime = 0;
let playbackGeneration = 0;
let isConnecting = false;
let isMicRunning = false;
let assistantLine = '';
let sttPartialLine = '';
let activeUtteranceId = '';
let nextSentenceSeqToPlay = 0;
let sentencePlaybackStates = new Map();
let drainPlaybackPromise = Promise.resolve();
let pendingPcmBytes = new Uint8Array(0);
const logEl = mustGet('log');
const cidEl = mustGet('cid');
const wsUrlEl = mustGet('wsUrl');
const promptEl = mustGet('prompt');
const connectBtn = mustGet('connectBtn');
const disconnectBtn = mustGet('disconnectBtn');
const sendBtn = mustGet('sendBtn');
const startMicBtn = mustGet('startMicBtn');
const stopMicBtn = mustGet('stopMicBtn');
const stopAssistantBtn = mustGet('stopAssistantBtn');
function mustGet(id) {
    const el = document.getElementById(id);
    if (!el)
        throw new Error(`Missing element #${id}`);
    return el;
}
function log(msg) {
    logEl.textContent += `${msg}\n`;
    logEl.scrollTop = logEl.scrollHeight;
}
function rewriteLastLines(prefix, next) {
    const lines = (logEl.textContent ?? '').split('\n');
    for (let i = lines.length - 1; i >= 0; i -= 1) {
        // @ts-ignore
        if (lines[i].startsWith(prefix)) {
            lines[i] = `${prefix}${next}`;
            logEl.textContent = lines.join('\n');
            logEl.scrollTop = logEl.scrollHeight;
            return;
        }
    }
    log(`${prefix}${next}`);
}
function currentCid() {
    return cidEl.value.trim() || 'dev';
}
function buildWsUrl() {
    const raw = wsUrlEl.value.trim();
    if (!raw)
        throw new Error('WebSocket URL is empty');
    const url = new URL(raw, window.location.href);
    url.searchParams.set('cid', currentCid());
    return url.toString();
}
function isSocketOpen() {
    return !!ws && ws.readyState === WebSocket.OPEN;
}
function updateUiState() {
    const connected = isSocketOpen();
    connectBtn.disabled = connected || isConnecting;
    disconnectBtn.disabled = !connected && !isConnecting;
    sendBtn.disabled = !connected;
    startMicBtn.disabled = !connected || isMicRunning;
    stopMicBtn.disabled = !isMicRunning;
    stopAssistantBtn.disabled = !connected;
}
function parseMessage(raw) {
    try {
        return JSON.parse(raw);
    }
    catch (err) {
        log(`[parse-error] ${err.message}`);
        return null;
    }
}
async function connect() {
    if (isSocketOpen() || isConnecting) {
        log('[info] websocket already connected or connecting');
        return;
    }
    try {
        isConnecting = true;
        updateUiState();
        const url = buildWsUrl();
        ws = new WebSocket(url);
        ws.binaryType = 'arraybuffer';
        ws.onopen = () => {
            isConnecting = false;
            log(`[open] ${url}`);
            sendJson({ type: 'ping', cid: currentCid() });
            updateUiState();
        };
        ws.onclose = () => {
            isConnecting = false;
            log('[closed]');
            ws = null;
            resetPlaybackSchedule();
            updateUiState();
        };
        ws.onerror = () => {
            log('[error] websocket');
        };
        ws.onmessage = async (event) => {
            const msg = parseMessage(event.data);
            if (!msg)
                return;
            switch (msg.type) {
                case 'assistant_start':
                    assistantLine = '';
                    activeUtteranceId = '';
                    resetPlaybackSchedule();
                    log('[assistant_start]');
                    break;
                case 'assistant_text':
                    assistantLine += msg.text ?? '';
                    rewriteLastLines('assistant: ', assistantLine);
                    break;
                case 'assistant_sentence':
                    if (typeof msg.utteranceId === 'string') {
                        activeUtteranceId = msg.utteranceId;
                    }
                    log(`[tts_queue] #${msg.seq ?? 0} ${msg.text ?? ''}`);
                    break;
                case 'assistant_audio':
                    if (msg.audioBase64) {
                        queueSentenceAudio(String(msg.utteranceId ?? activeUtteranceId ?? ''), Number(msg.seq ?? 0), Number(msg.chunkIndex ?? 0), String(msg.audioBase64), String(msg.audioFormat ?? 'pcm'));
                    }
                    break;
                case 'assistant_audio_complete':
                    markSentenceAudioComplete(String(msg.utteranceId ?? activeUtteranceId ?? ''), Number(msg.seq ?? 0));
                    break;
                case 'stt_partial':
                    sttPartialLine = msg.text ?? '';
                    rewriteLastLines('stt: ', sttPartialLine);
                    break;
                case 'stt_final':
                    sttPartialLine = '';
                    log(`user: ${msg.text ?? ''}`);
                    break;
                case 'error':
                    log(`assistant: [server-error] ${msg.message ?? 'unknown error'}`);
                    break;
                case 'connected':
                    log(`[connected] cid=${msg.cid ?? ''}`);
                    break;
                case 'assistant_finish':
                case 'assistant_done':
                case 'stopped':
                case 'pong':
                case 'client_stop':
                    log(`[${msg.type}]`);
                    if (msg.type === 'assistant_done') {
                        await flushPendingPcm();
                        assistantLine = '';
                    }
                    if (msg.type === 'stopped') {
                        assistantLine = '';
                    }
                    if (msg.type === 'stopped' || msg.type === 'client_stop') {
                        resetPlaybackSchedule();
                    }
                    break;
                default:
                    log(JSON.stringify(msg));
            }
        };
    }
    catch (err) {
        isConnecting = false;
        updateUiState();
        log(`[connect-error] ${err.message}`);
    }
    finally {
        updateUiState();
    }
}
function disconnect() {
    stopMic();
    resetPlaybackSchedule();
    if (ws) {
        ws.close();
    }
    ws = null;
    updateUiState();
}
function sendJson(payload) {
    if (!isSocketOpen() || !ws) {
        log('not connected');
        return;
    }
    ws.send(JSON.stringify(payload));
}
function sendText() {
    const text = promptEl.value.trim();
    if (!text) {
        log('[warn] empty text');
        return;
    }
    sendJson({ type: 'chat', cid: currentCid(), text });
}
async function ensurePlaybackContext() {
    if (!playbackCtx || playbackCtx.state === 'closed') {
        const AudioContextCtor = window.AudioContext || window.webkitAudioContext;
        playbackCtx = new AudioContextCtor({ sampleRate: PLAYBACK_SAMPLE_RATE });
        playbackCursorTime = 0;
    }
    if (playbackCtx.state === 'suspended') {
        await playbackCtx.resume();
    }
    return playbackCtx;
}
function resetPlaybackSchedule() {
    playbackGeneration += 1;
    playbackCursorTime = 0;
    activeUtteranceId = '';
    nextSentenceSeqToPlay = 0;
    sentencePlaybackStates = new Map();
    drainPlaybackPromise = Promise.resolve();
    pendingPcmBytes = new Uint8Array(0);
    if (playbackCtx && playbackCtx.state !== 'closed') {
        void playbackCtx.close();
    }
    playbackCtx = null;
}
async function startMic() {
    if (!isSocketOpen() || !ws) {
        log('not connected');
        return;
    }
    if (isMicRunning) {
        log('[info] mic already started');
        return;
    }
    try {
        mediaStream = await navigator.mediaDevices.getUserMedia({
            audio: {
                channelCount: 1,
                noiseSuppression: true,
                echoCancellation: true,
                autoGainControl: true,
            },
        });
        audioCtx = new AudioContext();
        if (audioCtx.state === 'suspended') {
            await audioCtx.resume();
        }
        sourceNode = audioCtx.createMediaStreamSource(mediaStream);
        processorNode = audioCtx.createScriptProcessor(4096, 1, 1);
        sourceNode.connect(processorNode);
        processorNode.connect(audioCtx.destination);
        processorNode.onaudioprocess = (event) => {
            const socket = ws;
            if (!socket || socket.readyState !== WebSocket.OPEN || !audioCtx) {
                return;
            }
            const input = event.inputBuffer.getChannelData(0);
            const downsampled = downsample(input, audioCtx.sampleRate, 16000);
            const pcm16 = floatTo16BitPCM(downsampled);
            if (pcm16.byteLength > 0) {
                socket.send(pcm16.buffer);
            }
        };
        isMicRunning = true;
        updateUiState();
        log(`[mic] started, inputRate=${audioCtx.sampleRate}`);
    }
    catch (err) {
        log(`[mic-error] ${err.message}`);
        stopMic();
    }
}
function stopMic() {
    if (processorNode) {
        processorNode.onaudioprocess = null;
        processorNode.disconnect();
    }
    if (sourceNode) {
        sourceNode.disconnect();
    }
    if (mediaStream) {
        mediaStream.getTracks().forEach((track) => track.stop());
    }
    if (audioCtx && audioCtx.state !== 'closed') {
        void audioCtx.close();
    }
    processorNode = null;
    sourceNode = null;
    mediaStream = null;
    audioCtx = null;
    isMicRunning = false;
    updateUiState();
    log('[mic] stopped');
}
function stopAssistant() {
    resetPlaybackSchedule();
    sendJson({ type: 'stop', cid: currentCid() });
}
function downsample(input, inputRate, outputRate) {
    if (outputRate >= inputRate)
        return input;
    const ratio = inputRate / outputRate;
    const newLength = Math.max(1, Math.round(input.length / ratio));
    const result = new Float32Array(newLength);
    let offsetResult = 0;
    let offsetBuffer = 0;
    while (offsetResult < result.length) {
        const nextOffsetBuffer = Math.min(input.length, Math.round((offsetResult + 1) * ratio));
        let accum = 0;
        let count = 0;
        for (let i = offsetBuffer; i < nextOffsetBuffer; i += 1) {
            accum += input[i] ?? 0;
            count += 1;
        }
        result[offsetResult] = count > 0 ? accum / count : 0;
        offsetResult += 1;
        offsetBuffer = nextOffsetBuffer;
    }
    return result;
}
function floatTo16BitPCM(input) {
    const out = new Int16Array(input.length);
    for (let i = 0; i < input.length; i += 1) {
        const s = Math.max(-1, Math.min(1, input[i] ?? 0));
        out[i] = s < 0 ? Math.round(s * 0x8000) : Math.round(s * 0x7fff);
    }
    return out;
}
function queueSentenceAudio(utteranceId, seq, chunkIndex, base64, format) {
    if (utteranceId && activeUtteranceId && utteranceId !== activeUtteranceId) {
        return;
    }
    if (utteranceId && !activeUtteranceId) {
        activeUtteranceId = utteranceId;
    }
    const state = sentencePlaybackStates.get(seq) ??
        { chunks: new Map(), nextChunkIndex: 0, complete: false };
    state.chunks.set(chunkIndex, { chunkIndex, base64, format });
    sentencePlaybackStates.set(seq, state);
    drainSentencePlayback();
}
function markSentenceAudioComplete(utteranceId, seq) {
    if (utteranceId && activeUtteranceId && utteranceId !== activeUtteranceId) {
        return;
    }
    if (utteranceId && !activeUtteranceId) {
        activeUtteranceId = utteranceId;
    }
    const state = sentencePlaybackStates.get(seq) ??
        { chunks: new Map(), nextChunkIndex: 0, complete: false };
    state.complete = true;
    sentencePlaybackStates.set(seq, state);
    drainSentencePlayback();
}
function drainSentencePlayback() {
    const generation = playbackGeneration;
    drainPlaybackPromise = drainPlaybackPromise
        .then(async () => {
        if (generation !== playbackGeneration) {
            return;
        }
        while (generation === playbackGeneration) {
            const state = sentencePlaybackStates.get(nextSentenceSeqToPlay);
            if (!state) {
                return;
            }
            let advanced = false;
            while (generation === playbackGeneration) {
                const chunk = state.chunks.get(state.nextChunkIndex);
                if (!chunk) {
                    break;
                }
                state.chunks.delete(state.nextChunkIndex);
                state.nextChunkIndex += 1;
                advanced = true;
                await queueAudio(chunk.base64, chunk.format);
            }
            if (state.complete && state.chunks.size === 0) {
                await flushPendingPcm();
                sentencePlaybackStates.delete(nextSentenceSeqToPlay);
                nextSentenceSeqToPlay += 1;
                advanced = true;
                continue;
            }
            if (!advanced) {
                return;
            }
        }
    })
        .catch((err) => {
        log(`[audio-error] ${err.message}`);
        console.error(err);
    });
}
async function queueAudio(base64, format) {
    const bytes = base64ToBytes(base64);
    if (bytes.byteLength === 0) {
        return;
    }
    const normalizedFormat = normalizeAudioFormat(format, bytes);
    if (normalizedFormat === 'pcm') {
        pendingPcmBytes = concatBytes(pendingPcmBytes, bytes);
        if (pendingPcmBytes.byteLength >= PCM_MIN_BUFFER_BYTES) {
            const readyBytes = pendingPcmBytes;
            pendingPcmBytes = new Uint8Array(0);
            await schedulePcmBytes(readyBytes);
        }
        return;
    }
    await flushPendingPcm();
    await playDecodedChunk(bytes);
}
function normalizeAudioFormat(format, bytes) {
    const lowered = (format ?? '').trim().toLowerCase();
    if (looksLikeWav(bytes)) {
        return 'wav';
    }
    if (lowered === 'pcm' || lowered === 'wav' || lowered === 'mp3' || lowered === 'opus' || lowered === 'aac' || lowered === 'flac') {
        return lowered;
    }
    return 'pcm';
}
function base64ToBytes(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
        bytes[i] = binary.charCodeAt(i) & 0xff;
    }
    return bytes;
}
function looksLikeWav(bytes) {
    return (bytes.byteLength >= 12 &&
        bytes[0] === 0x52 &&
        bytes[1] === 0x49 &&
        bytes[2] === 0x46 &&
        bytes[3] === 0x46 &&
        bytes[8] === 0x57 &&
        bytes[9] === 0x41 &&
        bytes[10] === 0x56 &&
        bytes[11] === 0x45);
}
function concatBytes(a, b) {
    if (a.byteLength === 0)
        return b;
    if (b.byteLength === 0)
        return a;
    const out = new Uint8Array(a.byteLength + b.byteLength);
    out.set(a, 0);
    out.set(b, a.byteLength);
    return out;
}
async function flushPendingPcm() {
    if (pendingPcmBytes.byteLength < 2) {
        pendingPcmBytes = new Uint8Array(0);
        return;
    }
    const readyBytes = pendingPcmBytes;
    pendingPcmBytes = new Uint8Array(0);
    await schedulePcmBytes(readyBytes);
}
async function schedulePcmBytes(bytes) {
    const generation = playbackGeneration;
    const ctx = await ensurePlaybackContext();
    if (generation !== playbackGeneration) {
        return;
    }
    const evenLength = bytes.byteLength - (bytes.byteLength % 2);
    if (evenLength < 2) {
        return;
    }
    const view = new DataView(bytes.buffer, bytes.byteOffset, evenLength);
    const frameCount = evenLength / 2;
    const audioBuffer = ctx.createBuffer(1, frameCount, PLAYBACK_SAMPLE_RATE);
    const channel = audioBuffer.getChannelData(0);
    for (let i = 0; i < frameCount; i += 1) {
        const sample = view.getInt16(i * 2, true);
        channel[i] = sample / 32768;
    }
    applyFade(channel);
    const now = ctx.currentTime;
    const startAt = Math.max(now + PLAYBACK_LEAD_SECONDS, playbackCursorTime || 0);
    const source = ctx.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(ctx.destination);
    source.start(startAt);
    playbackCursorTime = startAt + audioBuffer.duration;
}
function applyFade(channel) {
    const fadeSamples = Math.min(PCM_FADE_SAMPLES, Math.floor(channel.length / 2));
    if (fadeSamples <= 0) {
        return;
    }
    for (let i = 0; i < fadeSamples; i += 1) {
        const gainIn = i / fadeSamples;
        const gainOut = (fadeSamples - i) / fadeSamples;
        channel[i] = (channel[i] ?? 0) * gainIn;
        const tailIndex = channel.length - 1 - i;
        channel[tailIndex] = (channel[tailIndex] ?? 0) * gainOut;
    }
}
async function playDecodedChunk(bytes) {
    const generation = playbackGeneration;
    const ctx = await ensurePlaybackContext();
    if (generation !== playbackGeneration) {
        return;
    }
    const audioBuffer = await ctx.decodeAudioData(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
    const startAt = Math.max(ctx.currentTime + PLAYBACK_LEAD_SECONDS, playbackCursorTime || 0);
    const source = ctx.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(ctx.destination);
    source.start(startAt);
    playbackCursorTime = startAt + audioBuffer.duration;
}
connectBtn.onclick = () => void connect();
disconnectBtn.onclick = () => disconnect();
sendBtn.onclick = () => sendText();
startMicBtn.onclick = () => void startMic();
stopMicBtn.onclick = () => stopMic();
stopAssistantBtn.onclick = () => stopAssistant();
promptEl.addEventListener('keydown', (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
        sendText();
    }
});
updateUiState();
export {};
//# sourceMappingURL=main.js.map