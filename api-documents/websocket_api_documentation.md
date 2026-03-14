# WebSocket API Documentation

## Overview

This document describes the WebSocket communication protocol between the
browser frontend and the backend server. The system supports:

-   Text chat
-   Real-time speech-to-text (STT)
-   Streaming AI responses
-   Streaming text-to-speech (TTS) audio
-   Heartbeat and connection control

The connection is persistent and uses JSON messages for control events
and binary messages for microphone audio.

---

# 1. Connection

## WebSocket Endpoint

    ws://<host>/ws?cid=<client_id>

Example:

    ws://localhost:8080/ws?cid=dev

### Query Parameters

| Parameter | Type   | Description                                         |
|-----------|--------|-----------------------------------------------------|
| cid       | string | Client session ID used to identify the conversation |

After a successful connection, the server sends:

``` json
{
  "type": "connected",
  "cid": "dev"
}
```

---

# 2. Message Types

Two message formats exist:

1.  **JSON Text Messages** (control / events)
2.  **Binary Messages** (audio stream from microphone)

---

# 3. Client → Server Messages

## 3.1 Chat Message

Send a user text message.

``` json
{
  "type": "chat",
  "cid": "dev",
  "text": "Hello!"
}
```
| Field | Type   | Required | Description           |
|-------|--------|----------|-----------------------|
| type  | string | Yes      | Message type (`chat`) |
| cid   | string | Yes      | Conversation ID       |
| text  | string | Yes      | User input text       |

---

## 3.2 Stop Generation

Stops the assistant response.

``` json
{
  "type": "stop",
  "cid": "dev"
}
```

Used when the user interrupts the assistant.

---

## 3.3 Heartbeat

Client sends heartbeat to keep the connection alive.

``` json
{
  "type": "ping",
  "cid": "dev"
}
```

Server response:

``` json
{
  "type": "pong",
  "cid": "dev"
}
```

---

## 3.4 Voice Input (Binary)

Microphone audio is streamed as **binary PCM16 data**.

Process:

1.  Capture microphone audio
2.  Downsample to **16 kHz**
3.  Convert to **PCM16**
4.  Send binary frame through WebSocket

Example flow:

    Client -> Binary PCM audio chunks

---

# 4. Server → Client Messages

All server responses are JSON.

---

## 4.1 Assistant Start

Indicates that the assistant has started generating a response.

``` json
{
  "type": "assistant_start",
  "cid": "dev"
}
```

---

## 4.2 Assistant Text Token

Streaming text tokens produced by the assistant.

``` json
{
  "type": "assistant_text",
  "cid": "dev",
  "text": "Hello!"
}
```

These tokens should be concatenated on the client to build the final
message.

----

## 4.3 Assistant Sentence (TTS Queue)

Indicates a sentence has been queued for speech synthesis.

``` json
{
  "type": "assistant_sentence",
  "cid": "dev",
  "utteranceId": "abc123",
  "seq": 1,
  "text": "Hello, nice to meet you"
}
```

  Field         Description
  ------------- -----------------------
  utteranceId   TTS request ID
  seq           Audio sequence number
  text          Sentence content

---

## 4.4 Assistant Audio Chunk

Audio chunk for speech playback.

``` json
{
  "type": "assistant_audio",
  "cid": "dev",
  "utteranceId": "abc123",
  "seq": 1,
  "audioFormat": "pcm",
  "audioBase64": "BASE64_AUDIO_DATA"
}
```
| Field       | Description                |
|-------------|----------------------------|
| audioFormat | Audio encoding format      |
| audioBase64 | Base64 encoded audio bytes |

The client decodes the base64 data and schedules playback.

---

## 4.5 Assistant Finish

Indicates text generation has finished.

``` json
{
  "type": "assistant_finish",
  "cid": "dev"
}
```

---

## 4.6 Assistant Done

Indicates the entire response pipeline has completed.

``` json
{
  "type": "assistant_done",
  "cid": "dev"
}
```

---

## 4.7 Speech Recognition (STT)

### Partial Transcription

``` json
{
  "type": "stt_partial",
  "cid": "dev",
  "text": "Hel"
}
```

### Final Transcription

``` json
{
  "type": "stt_final",
  "cid": "dev",
  "text": "Hello"
}
```

---

## 4.8 Stop Events

Server stopping the conversation:

``` json
{
  "type": "stopped",
  "cid": "dev"
}
```

Client stop confirmation:

``` json
{
  "type": "client_stop",
  "cid": "dev"
}
```

---

## 4.9 Error Message

``` json
{
  "type": "error",
  "cid": "dev",
  "message": "Error description"
}
```

---

# 5. Audio Playback

Assistant audio is streamed in chunks.

Client playback pipeline:

1.  Decode Base64 audio
2.  Convert PCM16 to Float32
3.  Create `AudioBuffer`
4.  Schedule playback using `AudioContext`

Audio is queued sequentially to ensure smooth playback.

---

# 6. Connection Lifecycle

### Connection

    Client -> connect websocket
    Server -> connected

### Text Conversation

    Client -> chat
    Server -> assistant_start
    Server -> assistant_text
    Server -> assistant_finish
    Server -> assistant_done

### Voice Conversation

    Client -> binary audio
    Server -> stt_partial
    Server -> stt_final
    Server -> assistant_start
    Server -> assistant_text
    Server -> assistant_audio
    Server -> assistant_done

### Heartbeat

    Client -> ping
    Server -> pong

---

# 7. Recommended Project Documentation Structure

    docs/
     ├ websocket_api.md
     ├ websocket_protocol.md
     ├ audio_streaming.md
