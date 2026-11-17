const REFRESH_INTERVAL_MS = 2000;

const SERVICE_LABELS = {
    'payment-service': 'Zahlung',
    'inventory-service': 'Lager',
};

const STATE_LABELS = {
    CREATED: 'erstellt, nicht gestartet',
    RUNNING: 'läuft',
    PAUSED: 'pausiert',
    RESTARTING: 'startet neu',
    REMOVING: 'wird entfernt',
    EXITED: 'gestoppt',
    DEAD: 'abgestürzt',
    UNKNOWN: 'Zustand unbekannt',
};

// Which container actions make sense in which state, UNKNOWN allows everything.
const ALLOWED_ACTIONS = {
    CREATED: ['start'],
    RUNNING: ['stop', 'pause'],
    PAUSED: ['unpause'],
    RESTARTING: [],
    REMOVING: [],
    EXITED: ['start'],
    DEAD: ['start'],
    UNKNOWN: ['stop', 'start', 'pause', 'unpause'],
};

const servicesElement = document.getElementById('services');
const messageElement = document.getElementById('message');
const template = document.getElementById('service-template');
const cards = new Map();

async function request(method, path, body) {
    const options = {method};
    if (body) {
        options.headers = {'Content-Type': 'application/json'};
        options.body = JSON.stringify(body);
    }
    const response = await fetch(path, options);
    if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.detail ?? `${method} ${path} antwortet mit ${response.status}`);
    }
    return response.json();
}

function showMessage(text) {
    messageElement.textContent = text;
    messageElement.hidden = !text;
}

async function run(action) {
    try {
        await action();
        showMessage('');
    } catch (e) {
        showMessage(e.message);
    }
    await refresh();
}

function createCard(name) {
    const card = template.content.firstElementChild.cloneNode(true);
    card.querySelector('.service-name').textContent = SERVICE_LABELS[name] ?? name;

    const form = card.querySelector('.chaos');
    form.addEventListener('submit', event => {
        event.preventDefault();
        run(() => request('POST', `/services/${name}/chaos`, {
            latencyMs: Number(form.elements.latencyMs.value),
            errorRate: Number(form.elements.errorRate.value),
        }));
    });
    card.querySelector('.reset').addEventListener('click', () => {
        form.elements.latencyMs.value = 0;
        form.elements.errorRate.value = 0;
        run(() => request('POST', `/services/${name}/chaos`, {latencyMs: 0, errorRate: 0}));
    });
    card.querySelectorAll('[data-action]').forEach(button =>
        button.addEventListener('click', () =>
            run(() => request('POST', `/services/${name}/${button.dataset.action}`))));

    servicesElement.appendChild(card);
    return card;
}

function render(service) {
    let card = cards.get(service.name);
    if (!card) {
        card = createCard(service.name);
        cards.set(service.name, card);
    }
    const form = card.querySelector('.chaos');
    if (service.chaos && !form.dataset.filled) {
        form.elements.latencyMs.value = service.chaos.latencyMs;
        form.elements.errorRate.value = service.chaos.errorRate;
        form.dataset.filled = 'true';
    }

    card.dataset.state = service.containerState;
    card.querySelector('.service-state').textContent = STATE_LABELS[service.containerState] ?? service.containerState;

    const chaos = service.chaos;
    card.querySelector('.chaos-current').textContent = chaos
        ? `Aktuell: ${chaos.latencyMs} ms Verzögerung, ${Math.round(chaos.errorRate * 100)} % Fehler`
        : 'Aktuell: Dienst antwortet nicht';
    card.querySelectorAll('.chaos input, .chaos button').forEach(element => element.disabled = !chaos);

    const allowed = ALLOWED_ACTIONS[service.containerState] ?? ALLOWED_ACTIONS.UNKNOWN;
    card.querySelectorAll('[data-action]').forEach(button =>
        button.disabled = !allowed.includes(button.dataset.action));
}

async function refresh() {
    try {
        const services = await request('GET', '/services');
        services.forEach(render);
    } catch (e) {
        showMessage(e.message);
    }
}

refresh();
setInterval(refresh, REFRESH_INTERVAL_MS);
