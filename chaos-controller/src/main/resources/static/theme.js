// Loaded before the page is drawn, so a dark page never flashes light first.
(() => {
    let theme = null;
    try {
        theme = localStorage.getItem('chaos-playground.theme');
    } catch {
        // storage blocked, e.g. in a private window: follow the system setting
    }
    if (theme !== 'light' && theme !== 'dark') {
        theme = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    document.documentElement.dataset.theme = theme;
})();
