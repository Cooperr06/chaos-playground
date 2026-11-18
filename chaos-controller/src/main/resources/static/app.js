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

// kiosk hides the Grafana menu and search, the time range and refresh controls stay
const DASHBOARD_PATH = '/d/chaos-playground/chaos-playground?orgId=1&refresh=5s&from=now-15m&to=now&kiosk';

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

const loadForm = document.getElementById('load-form');
const loadRate = document.getElementById('load-rate');
const loadValue = document.getElementById('load-value');
const loadCurrent = document.getElementById('load-current');

// slider and number field show the same value, either one can be used
loadRate.addEventListener('input', () => loadValue.value = loadRate.value);
loadValue.addEventListener('input', () => loadRate.value = loadValue.value);
loadForm.addEventListener('submit', event => {
    event.preventDefault();
    run(() => request('POST', '/load', {ratePerSecond: Number(loadValue.value)}));
});

function renderLoad(load) {
    loadCurrent.textContent = load.ratePerSecond === 0
        ? 'Aktuell: keine Bestellungen, die Graphen bleiben leer.'
        : `Aktuell: ${load.ratePerSecond} pro Sekunde.`;
    if (!loadForm.dataset.filled) {
        loadRate.value = load.ratePerSecond;
        loadValue.value = load.ratePerSecond;
        loadForm.dataset.filled = 'true';
    }
}

async function refresh() {
    // independent requests: an unreachable load generator must not hide the services
    const [services, load] = await Promise.allSettled([request('GET', '/services'), request('GET', '/load')]);
    if (services.status === 'fulfilled') {
        services.value.forEach(render);
    }
    if (load.status === 'fulfilled') {
        renderLoad(load.value);
    }
    const failed = [services, load].find(result => result.status === 'rejected');
    if (failed) {
        showMessage(failed.reason.message);
    }
}

let grafanaUrl = null;

function loadGraphs() {
    if (grafanaUrl) {
        // Grafana takes its theme from the URL, so switching the theme reloads the graphs
        document.getElementById('graphs').src = `${grafanaUrl}${DASHBOARD_PATH}&theme=${document.documentElement.dataset.theme}`;
    }
}

async function showGraphs() {
    try {
        grafanaUrl = (await request('GET', '/config')).grafanaUrl;
        loadGraphs();
    } catch (e) {
        showMessage(e.message);
    }
}

const THEME_KEY = 'chaos-playground.theme';
const themeToggle = document.getElementById('theme-toggle');

function renderThemeToggle() {
    const label = document.documentElement.dataset.theme === 'dark' ? 'Helles Design' : 'Dunkles Design';
    themeToggle.querySelector('.theme-label').textContent = label;
    themeToggle.title = label;
}

themeToggle.addEventListener('click', () => {
    const theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = theme;
    try {
        localStorage.setItem(THEME_KEY, theme);
    } catch {
        // storage blocked: the choice only lasts until the page is reloaded
    }
    renderThemeToggle();
    loadGraphs();
});
renderThemeToggle();

const SIDEBAR_KEY = 'chaos-playground.sidebar-collapsed';
const sidebarToggle = document.getElementById('sidebar-toggle');

function setSidebarCollapsed(collapsed) {
    document.body.classList.toggle('sidebar-collapsed', collapsed);
    sidebarToggle.setAttribute('aria-expanded', String(!collapsed));
    sidebarToggle.querySelector('.toggle-label').textContent = collapsed ? 'Steuerung einblenden' : 'Steuerung ausblenden';
    sidebarToggle.title = collapsed ? 'Steuerung einblenden' : '';
    try {
        localStorage.setItem(SIDEBAR_KEY, String(collapsed));
    } catch {
        // storage blocked, e.g. in a private window: the sidebar just opens expanded next time
    }
}

function storedSidebarCollapsed() {
    try {
        return localStorage.getItem(SIDEBAR_KEY) === 'true';
    } catch {
        return false;
    }
}

sidebarToggle.addEventListener('click', () =>
    setSidebarCollapsed(!document.body.classList.contains('sidebar-collapsed')));
setSidebarCollapsed(storedSidebarCollapsed());

showGraphs();
refresh();
setInterval(refresh, REFRESH_INTERVAL_MS);
