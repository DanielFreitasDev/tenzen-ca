// Motion com anime.js v4, sempre atrás de prefers-reduced-motion.
import {animate, stagger, svg} from 'animejs';

function reduced() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

// Ao fim da animação, solta a camada de composição: will-change/transform
// residuais mantêm o elemento rasterizado como textura e deixam o texto
// embaçado (sobretudo em telas com escala fracionária).
function settle(elements) {
    for (const el of elements) {
        el.classList.remove('motion-item');
        el.style.removeProperty('opacity');
        el.style.removeProperty('transform');
    }
}

// Entrada escalonada dos filhos de [data-motion-stagger]
function staggerEntrances() {
    for (const container of document.querySelectorAll('[data-motion-stagger]')) {
        const items = [...container.children];
        if (!items.length) continue;
        items.forEach((el) => el.classList.add('motion-item'));
        animate(items, {
            opacity: [0, 1],
            translateY: [10, 0],
            duration: 420,
            delay: stagger(45),
            ease: 'outQuad',
            onComplete: () => settle(items),
        });
    }
}

// Reveal de sucesso: check SVG desenhado + card
function successReveal() {
    const card = document.querySelector('[data-success-card]');
    if (!card) return;
    const check = card.querySelector('[data-success-check]');
    animate(card, {
        opacity: [0, 1],
        scale: [0.97, 1],
        duration: 380,
        ease: 'outQuad',
        onComplete: () => settle([card]),
    });
    if (check) {
        animate(svg.createDrawable(check), {
            draw: ['0 0', '0 1'],
            duration: 650,
            delay: 220,
            ease: 'inOutQuad',
        });
    }
    const details = card.querySelectorAll('[data-success-detail]');
    if (details.length) {
        animate(details, {
            opacity: [0, 1],
            translateY: [8, 0],
            duration: 360,
            delay: stagger(40, {start: 320}),
            ease: 'outQuad',
            onComplete: () => settle([...details]),
        });
    }
}

export function initMotion() {
    if (reduced()) return;
    staggerEntrances();
    successReveal();
}

// Transição entre conjuntos de campos (e-CPF <-> e-CNPJ), usada por issue.js
export function swapFieldsets(hide, show) {
    if (reduced()) {
        hide.hidden = true;
        show.hidden = false;
        return;
    }
    animate(hide, {
        opacity: [1, 0],
        translateY: [0, -6],
        duration: 160,
        ease: 'inQuad',
        onComplete: () => {
            hide.hidden = true;
            settle([hide]);
            show.hidden = false;
            animate(show, {
                opacity: [0, 1],
                translateY: [6, 0],
                duration: 220,
                ease: 'outQuad',
                onComplete: () => settle([show]),
            });
        },
    });
}
