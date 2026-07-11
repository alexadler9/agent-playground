const MESSAGES_KEY = 'local-llm-chat-messages';
const TOKEN_KEY = 'local-llm-chat-token';
const MAX_MESSAGES = 20;

const chat = document.getElementById('chat');
const input = document.getElementById('messageInput');
const tokenInput = document.getElementById('tokenInput');
const sendButton = document.getElementById('sendButton');
const clearButton = document.getElementById('clearButton');
const statusDot = document.getElementById('statusDot');
const statusText = document.getElementById('statusText');

let messages = loadMessages();
let isSending = false;

tokenInput.value = localStorage.getItem(TOKEN_KEY) || '';

tokenInput.addEventListener('input', () => {
    localStorage.setItem(TOKEN_KEY, tokenInput.value);
});

sendButton.addEventListener('click', sendMessage);

input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
});

clearButton.addEventListener('click', () => {
    messages = [];
    saveMessages();
    renderMessages();
});

function loadMessages() {
    try {
        return JSON.parse(localStorage.getItem(MESSAGES_KEY) || '[]');
    } catch (_) {
        return [];
    }
}

function saveMessages() {
    messages = messages.slice(-MAX_MESSAGES);
    localStorage.setItem(MESSAGES_KEY, JSON.stringify(messages));
}

function renderMessages() {
    chat.innerHTML = '';

    if (messages.length === 0) {
        addBubble(
            'assistant',
            'Привет! Я приватный локальный LLM-чат. Введите токен сверху и отправьте сообщение.'
        );
        return;
    }

    messages.forEach((message) => {
        addBubble(message.role, message.content);
    });

    scrollToBottom();
}

function addBubble(role, text) {
    const row = document.createElement('div');
    row.className = 'message-row ' + role;

    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = text;

    row.appendChild(bubble);
    chat.appendChild(row);

    scrollToBottom();
}

function showTyping() {
    const row = document.createElement('div');
    row.id = 'typingRow';
    row.className = 'message-row assistant';

    const bubble = document.createElement('div');
    bubble.className = 'bubble';

    const typing = document.createElement('div');
    typing.className = 'typing';

    for (let i = 0; i < 3; i++) {
        typing.appendChild(document.createElement('span'));
    }

    bubble.appendChild(typing);
    row.appendChild(bubble);
    chat.appendChild(row);

    scrollToBottom();
}

function hideTyping() {
    const row = document.getElementById('typingRow');
    if (row) {
        row.remove();
    }
}

async function sendMessage() {
    if (isSending) return;

    const text = input.value.trim();
    const token = tokenInput.value.trim();

    if (!text) return;

    messages.push({ role: 'user', content: text });
    saveMessages();
    renderMessages();

    input.value = '';
    isSending = true;
    showTyping();

    try {
        const response = await fetch('/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token
            },
            body: JSON.stringify({
                messages: messages.slice(-MAX_MESSAGES)
            })
        });

        hideTyping();

        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({
                error: 'HTTP ' + response.status
            }));

            messages.push({
                role: 'assistant',
                content: 'Ошибка: ' + (errorBody.error || response.status)
            });

            saveMessages();
            renderMessages();
            return;
        }

        const data = await response.json();

        messages.push({
            role: 'assistant',
            content: data.answer
        });

        saveMessages();
        renderMessages();
    } catch (error) {
        hideTyping();

        messages.push({
            role: 'assistant',
            content: 'Ошибка сети: ' + error.message
        });

        saveMessages();
        renderMessages();
    } finally {
        isSending = false;
        input.focus();
    }
}

function scrollToBottom() {
    chat.scrollTop = chat.scrollHeight;
}

async function checkHealth() {
    try {
        const response = await fetch('/health');

        if (response.ok) {
            statusDot.classList.add('online');
            statusText.textContent = 'Online';
        } else {
            statusDot.classList.remove('online');
            statusText.textContent = 'Error';
        }
    } catch (_) {
        statusDot.classList.remove('online');
        statusText.textContent = 'Offline';
    }
}

renderMessages();
checkHealth();
setInterval(checkHealth, 10000);