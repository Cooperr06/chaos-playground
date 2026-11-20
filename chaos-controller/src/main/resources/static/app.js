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

// false until GET /me says otherwise, the server checks every POST anyway
let isAdmin = false;

// Spring Security puts the CSRF token into this cookie, every POST has to send it back as a header
function csrfToken() {
    const cookie = document.cookie.split('; ').find(entry => entry.startsWith('XSRF-TOKEN='));
    return cookie ? decodeURIComponent(cookie.substring('XSRF-TOKEN='.length)) : '';
}

async function request(method, path, body) {
    // without it fetch sends */*, Authelia then redirects to its login page instead of answering 401
    const options = {method, headers: {Accept: 'application/json'}};
    if (method !== 'GET') {
        options.headers['X-XSRF-TOKEN'] = csrfToken();
    }
    if (body) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }
    let response;
    try {
        response = await fetch(path, options);
    } catch {
        // no answer at all, e.g. "NetworkError when attempting to fetch resource." while the stack is down
        throw new UnreachableError();
    }
    if (response.status === 401) {
        // logged out in another tab or session expired: reloading the page leads to the Authelia login
        location.reload();
        throw new Error('Nicht mehr angemeldet, die Seite lädt neu.');
    }
    if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        // 502 to 504 without a problem detail come from Traefik, not from the controller: it is (re)starting
        if (!problem.detail && GATEWAY_ERRORS.includes(response.status)) {
            throw new UnreachableError();
        }
        throw new Error(problem.detail ?? `${method} ${path} antwortet mit ${response.status}`);
    }
    return response.json();
}

const GATEWAY_ERRORS = [502, 503, 504];

class UnreachableError extends Error {
    constructor() {
        super('Keine Verbindung zum Server, wahrscheinlich startet er gerade neu. Die Seite verbindet sich automatisch wieder.');
    }
}

let messageTimer;

// ok: green instead of red, for good news like a restored connection
function showMessage(text, ok = false) {
    clearTimeout(messageTimer);
    messageElement.textContent = text;
    messageElement.classList.toggle('ok', ok);
    messageElement.hidden = !text;
}

async function run(action) {
    try {
        await action();
        showMessage('');
    } catch (e) {
        showMessage(e.message);
        // the message belongs to the action now, the next successful refresh must not clear it
        refreshFailed = false;
    }
    await refresh();
}

function createCard(name) {
    const card = template.content.firstElementChild.cloneNode(true);
    card.querySelector('.service-name').textContent = SERVICE_LABELS[name] ?? name;

    const form = card.querySelector('.chaos');
    form.addEventListener('submit', event => {
        event.preventDefault();
        // novalidate also skips the check for text: Firefox and Safari accept letters, the field then reads as empty
        const invalid = [form.elements.latencyMs, form.elements.errorRate]
            .find(input => input.value.trim() === '' || input.validity.badInput);
        if (invalid) {
            showMessage(`${invalid.labels[0].querySelector('.label-text').firstChild.textContent.trim()}: bitte eine Zahl eingeben.`);
            invalid.focus();
            return;
        }
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
    card.querySelectorAll('.chaos input, .chaos button').forEach(element => element.disabled = !chaos || !isAdmin);

    const allowed = ALLOWED_ACTIONS[service.containerState] ?? ALLOWED_ACTIONS.UNKNOWN;
    card.querySelectorAll('[data-action]').forEach(button =>
        button.disabled = !allowed.includes(button.dataset.action) || !isAdmin);
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

// a message shown by refresh goes away by itself once refresh succeeds again
let refreshFailed = false;
let serverWasUnreachable = false;

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
        refreshFailed = true;
        serverWasUnreachable ||= failed.reason instanceof UnreachableError;
    } else if (refreshFailed) {
        refreshFailed = false;
        if (serverWasUnreachable) {
            serverWasUnreachable = false;
            showMessage('Verbindung wiederhergestellt.', true);
            messageTimer = setTimeout(() => showMessage(''), 5000);
        } else {
            showMessage('');
        }
    }
}

function renderLoadControls() {
    loadForm.querySelectorAll('input, button').forEach(element => element.disabled = !isAdmin);
}

async function showUser() {
    try {
        const user = await request('GET', '/me');
        isAdmin = user.admin;
        document.getElementById('user').textContent = user.name;
        document.getElementById('account-trigger').title = `Angemeldet als ${user.name} (${user.admin ? 'Admin' : 'Nur ansehen'})`;
        document.getElementById('account').hidden = false;
    } catch (e) {
        showMessage(e.message);
    }
    renderLoadControls();
}

let grafanaUrl = null;

function loadGraphs() {
    if (grafanaUrl) {
        // Grafana takes its theme from the URL, so switching the theme reloads the graphs.
        // replace instead of setting src: no history entry, browser back leaves the page instead of the old theme
        document.getElementById('graphs').contentWindow.location.replace(
            `${grafanaUrl}${DASHBOARD_PATH}&theme=${document.documentElement.dataset.theme}`);
    }
}

const logoutButton = document.getElementById('logout');

// rd: after logout Authelia sends the browser back here, which leads to its login and after that to the dashboard again
function showLogout(logoutUrl) {
    if (logoutUrl) {
        const target = `${logoutUrl}?rd=${encodeURIComponent(`${location.origin}/`)}`;
        logoutButton.addEventListener('click', () => location.href = target);
        document.getElementById('account-menu').hidden = false;
        document.getElementById('account-arrow').hidden = false;
    }
}

async function showConfig() {
    try {
        const config = await request('GET', '/config');
        grafanaUrl = config.grafanaUrl;
        loadGraphs();
        showLogout(config.logoutUrl);
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

renderLoadControls();
showUser();
showConfig();
refresh();
setInterval(refresh, REFRESH_INTERVAL_MS);
