// Tema claro/escuro com revelação circular via View Transitions API.
// O anti-FOUC fica num script inline (com nonce) no <head>; aqui é só o toggle.
const KEY = 'tenzen-theme';

export function currentTheme() {
    const stored = localStorage.getItem(KEY);
    if (stored === 'light' || stored === 'dark') return stored;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function apply(theme) {
    document.documentElement.classList.toggle('dark', theme === 'dark');
}

function reducedMotion() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

export function toggleTheme(event) {
    const next = currentTheme() === 'dark' ? 'light' : 'dark';
    localStorage.setItem(KEY, next);

    const root = document.documentElement;
    if (!document.startViewTransition || reducedMotion()) {
        apply(next);
        return;
    }

    // origem da revelação: centro do botão clicado (ou centro da tela)
    let x = window.innerWidth / 2;
    let y = window.innerHeight / 2;
    const target = event?.currentTarget;
    if (target instanceof Element) {
        const r = target.getBoundingClientRect();
        x = r.left + r.width / 2;
        y = r.top + r.height / 2;
    }
    const radius = Math.hypot(
        Math.max(x, window.innerWidth - x),
        Math.max(y, window.innerHeight - y),
    );
    root.style.setProperty('--reveal-x', `${x}px`);
    root.style.setProperty('--reveal-y', `${y}px`);
    root.style.setProperty('--reveal-r', `${radius}px`);
    root.classList.add('theme-reveal');

    const transition = document.startViewTransition(() => apply(next));
    transition.finished.finally(() => root.classList.remove('theme-reveal'));
}

export function initTheme() {
    for (const btn of document.querySelectorAll('[data-theme-toggle]')) {
        btn.addEventListener('click', toggleTheme);
    }
    // segue o sistema enquanto o usuário não escolher manualmente
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
        if (!localStorage.getItem(KEY)) apply(e.matches ? 'dark' : 'light');
    });
}
