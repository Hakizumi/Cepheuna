type StreamEventType = "status" | "delta" | "unknown";

interface StreamPayload {
    success?: boolean;
    message?: string;
    [key: string]: unknown;
}

interface ParsedEvent {
    event: StreamEventType;
    data: StreamPayload | null;
}

const messageList = document.getElementById("messageList") as HTMLDivElement;
const emptyState = document.getElementById("emptyState") as HTMLDivElement;
const messageInput = document.getElementById("messageInput") as HTMLTextAreaElement;
const sendButton = document.getElementById("sendButton") as HTMLButtonElement;
const cidInput = document.getElementById("cidInput") as HTMLInputElement;
const statusBadge = document.getElementById("statusBadge") as HTMLDivElement;

let isSending = false;

function setStatus(type: "idle" | "streaming" | "done" | "error", text: string): void {
    statusBadge.className = `status-badge ${type}`;
    statusBadge.textContent = text;
}

function autoResizeTextarea(): void {
    messageInput.style.height = "auto";
    messageInput.style.height = `${Math.min(messageInput.scrollHeight, 200)}px`;
}

function hideEmptyState(): void {
    if (emptyState) {
        emptyState.style.display = "none";
    }
}

function scrollToBottom(): void {
    messageList.scrollTop = messageList.scrollHeight;
}

function formatTime(): string {
    const now = new Date();
    return now.toLocaleTimeString("zh-CN", {
        hour: "2-digit",
        minute: "2-digit",
    });
}

function createMessageRow(role: "user" | "assistant", text: string): HTMLDivElement {
    const row = document.createElement("div");
    row.className = `message-row ${role}`;

    const avatar = document.createElement("div");
    avatar.className = `avatar ${role}`;
    avatar.textContent = role === "user" ? "U" : "AI";

    const contentWrap = document.createElement("div");

    const bubble = document.createElement("div");
    bubble.className = "message-bubble";
    bubble.textContent = text;

    const meta = document.createElement("div");
    meta.className = "message-meta";
    meta.textContent = role === "user" ? `你 · ${formatTime()}` : `助手 · ${formatTime()}`;

    contentWrap.appendChild(bubble);
    contentWrap.appendChild(meta);

    row.appendChild(avatar);
    row.appendChild(contentWrap);

    messageList.appendChild(row);
    scrollToBottom();

    return row;
}

function createAssistantStreamingMessage(): HTMLDivElement {
    const row = createMessageRow("assistant", "");
    const bubble = row.querySelector(".message-bubble") as HTMLDivElement;
    bubble.classList.add("typing-cursor");
    return row;
}

function updateAssistantMessage(row: HTMLDivElement, text: string, done = false): void {
    const bubble = row.querySelector(".message-bubble") as HTMLDivElement;
    bubble.textContent = text;

    if (done) {
        bubble.classList.remove("typing-cursor");
    } else {
        bubble.classList.add("typing-cursor");
    }

    scrollToBottom();
}

function parseSSEBlock(block: string): ParsedEvent | null {
    const lines = block.split(/\r?\n/);

    let eventName = "unknown";
    const dataLines: string[] = [];

    for (const rawLine of lines) {
        const line = rawLine.trim();
        if (!line) continue;

        if (line.startsWith("event:")) {
            eventName = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
            dataLines.push(line.slice(5).trim());
        }
    }

    const dataText = dataLines.join("\n");

    let parsedData: StreamPayload | null = null;
    if (dataText) {
        try {
            parsedData = JSON.parse(dataText) as StreamPayload;
        } catch (error) {
            console.error("JSON parse error:", error, dataText);
        }
    }

    const event =
        eventName === "status" || eventName === "delta"
            ? (eventName as StreamEventType)
            : "unknown";

    return {
        event,
        data: parsedData,
    };
}

async function sendMessage(): Promise<void> {
    if (isSending) return;

    const message = messageInput.value.trim();
    const cid = cidInput.value.trim() || "Conversation-id";

    if (!message) return;

    isSending = true;
    sendButton.disabled = true;
    hideEmptyState();

    createMessageRow("user", message);
    const assistantRow = createAssistantStreamingMessage();

    setStatus("streaming", "Streaming");

    let assistantText = "";

    try {
        const response = await fetch(
            "http://localhost:11622/backend/conversation/message_streaming",
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Accept: "text/event-stream",
                },
                body: JSON.stringify({
                    cid,
                    message,
                }),
            }
        );

        if (!response.ok) {
            throw new Error(`HTTP ${response.status} ${response.statusText}`);
        }

        if (!response.body) {
            throw new Error("响应体为空，无法读取流");
        }

        messageInput.value = "";
        autoResizeTextarea();

        const reader = response.body.getReader();
        const decoder = new TextDecoder("utf-8");

        let buffer = "";
        let streamFinished = false;

        while (!streamFinished) {
            const { value, done } = await reader.read();

            if (done) {
                break;
            }

            buffer += decoder.decode(value, { stream: true });

            /**
             * SSE 风格事件通常以空行分隔：
             * event: xxx
             * data: xxx
             *
             * event: xxx
             * data: xxx
             */
            const parts = buffer.split(/\r?\n\r?\n/);
            buffer = parts.pop() ?? "";

            for (const part of parts) {
                const parsed = parseSSEBlock(part);
                if (!parsed) continue;

                const payload = parsed.data;
                const text = payload?.message ?? "";

                if (parsed.event === "status") {
                    if (text === "start") {
                        setStatus("streaming", "Started");
                    } else if (text === "done") {
                        updateAssistantMessage(assistantRow, assistantText, true);
                        setStatus("done", "Done");
                        streamFinished = true;
                    }
                }

                if (parsed.event === "delta") {
                    assistantText += text;
                    updateAssistantMessage(assistantRow, assistantText, false);
                }
            }
        }

        if (!assistantText) {
            updateAssistantMessage(assistantRow, "（没有收到模型回复内容）", true);
        }
    } catch (error) {
        console.error("sendMessage error:", error);
        updateAssistantMessage(
            assistantRow,
            `请求失败：${error instanceof Error ? error.message : "未知错误"}`,
            true
        );
        setStatus("error", "Error");
    } finally {
        isSending = false;
        sendButton.disabled = false;
        scrollToBottom();
    }
}

sendButton.addEventListener("click", () => {
    void sendMessage();
});

messageInput.addEventListener("input", autoResizeTextarea);

messageInput.addEventListener("keydown", (event: KeyboardEvent) => {
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        void sendMessage();
    }
});

autoResizeTextarea();
setStatus("idle", "Idle");
