// Aprimoramento progressivo dos controles de formulário.
// HTML semântico por baixo (radios e <select> reais; o form funciona sem JS);
// por cima, UI custom seguindo o padrão APG "select-only combobox".

let comboSeq = 0;

function enhanceCombobox(select) {
    const wrapper = document.createElement('div');
    wrapper.className = 'relative';
    select.parentNode.insertBefore(wrapper, select);
    wrapper.appendChild(select);

    // o select continua no DOM (submissão + fallback), mas sai do fluxo de interação
    select.tabIndex = -1;
    select.setAttribute('aria-hidden', 'true');
    select.classList.add('sr-only');

    const id = `cbx-${comboSeq++}`;
    const labelEl = select.id ? document.querySelector(`label[for="${select.id}"]`) : null;

    const button = document.createElement('button');
    button.type = 'button';
    button.id = `${id}-btn`;
    button.setAttribute('role', 'combobox');
    button.setAttribute('aria-haspopup', 'listbox');
    button.setAttribute('aria-expanded', 'false');
    button.setAttribute('aria-controls', `${id}-list`);
    if (labelEl) {
        labelEl.id = labelEl.id || `${id}-label`;
        button.setAttribute('aria-labelledby', `${labelEl.id} ${id}-btn`);
        // clicar no rótulo deve focar o controle visível, não o select oculto
        labelEl.htmlFor = button.id;
    }
    button.className =
        'input flex items-center justify-between gap-2 text-left cursor-pointer';
    button.innerHTML =
        '<span data-value-label class="truncate"></span>' +
        '<svg aria-hidden="true" class="size-4 shrink-0 text-muted-foreground" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4 6l4 4 4-4"/></svg>';

    const list = document.createElement('ul');
    list.id = `${id}-list`;
    list.setAttribute('role', 'listbox');
    list.tabIndex = -1;
    list.className =
        'absolute z-30 mt-1 hidden max-h-64 w-full overflow-auto rounded-lg border border-border bg-card p-1 shadow-lg';

    const options = [...select.options].map((opt, i) => {
        const li = document.createElement('li');
        li.id = `${id}-opt-${i}`;
        li.setAttribute('role', 'option');
        li.dataset.value = opt.value;
        li.textContent = opt.textContent;
        li.className =
            'cursor-pointer rounded-sm px-2.5 py-1.5 text-sm data-active:bg-accent-soft ' +
            'aria-selected:font-medium aria-selected:text-primary';
        list.appendChild(li);
        return li;
    });

    wrapper.appendChild(button);
    wrapper.appendChild(list);

    let active = Math.max(0, select.selectedIndex);
    let open = false;
    let typeahead = '';
    let typeaheadTimer = 0;

    const valueLabel = button.querySelector('[data-value-label]');

    function render() {
        valueLabel.textContent = select.options[select.selectedIndex]?.textContent ?? '';
        options.forEach((li, i) => {
            li.setAttribute('aria-selected', String(i === select.selectedIndex));
            if (i === active) li.setAttribute('data-active', '');
            else li.removeAttribute('data-active');
        });
        button.setAttribute('aria-activedescendant', open ? options[active].id : '');
    }

    function setOpen(next) {
        open = next;
        list.classList.toggle('hidden', !open);
        button.setAttribute('aria-expanded', String(open));
        if (open) {
            active = Math.max(0, select.selectedIndex);
            render();
            options[active]?.scrollIntoView({block: 'nearest'});
        } else {
            render();
        }
    }

    function commit(i) {
        if (select.selectedIndex !== i) {
            select.selectedIndex = i;
            select.dispatchEvent(new Event('change', {bubbles: true}));
        }
        setOpen(false);
    }

    function move(delta) {
        active = Math.min(options.length - 1, Math.max(0, active + delta));
        render();
        options[active]?.scrollIntoView({block: 'nearest'});
    }

    button.addEventListener('click', () => setOpen(!open));
    button.addEventListener('keydown', (e) => {
        const {key} = e;
        if (key === 'ArrowDown' || key === 'ArrowUp') {
            e.preventDefault();
            if (!open) setOpen(true);
            else move(key === 'ArrowDown' ? 1 : -1);
        } else if (key === 'Home' || key === 'End') {
            if (!open) return;
            e.preventDefault();
            active = key === 'Home' ? 0 : options.length - 1;
            render();
        } else if (key === 'Enter' || key === ' ') {
            e.preventDefault();
            if (open) commit(active);
            else setOpen(true);
        } else if (key === 'Escape') {
            if (open) {
                e.preventDefault();
                setOpen(false);
            }
        } else if (key === 'Tab') {
            if (open) commit(active);
        } else if (/^[a-z0-9]$/i.test(key)) {
            // type-ahead
            clearTimeout(typeaheadTimer);
            typeahead += key.toLowerCase();
            typeaheadTimer = setTimeout(() => (typeahead = ''), 500);
            const start = open ? active : select.selectedIndex;
            const order = [...options.keys()].map((i) => (start + 1 + i) % options.length);
            const hit = order.find((i) =>
                options[i].textContent.trim().toLowerCase().startsWith(typeahead),
            );
            if (hit !== undefined) {
                if (open) {
                    active = hit;
                    render();
                    options[hit].scrollIntoView({block: 'nearest'});
                } else {
                    commit(hit);
                }
            }
        }
    });

    options.forEach((li, i) => {
        li.addEventListener('pointermove', () => {
            if (active !== i) {
                active = i;
                render();
            }
        });
        li.addEventListener('click', () => commit(i));
    });

    document.addEventListener('pointerdown', (e) => {
        if (open && !wrapper.contains(e.target)) setOpen(false);
    });

    // mudanças programáticas no select (ex.: reset do form)
    select.addEventListener('change', render);
    select.form?.addEventListener('reset', () => setTimeout(render));

    render();
}

export function initControls(root = document) {
    for (const select of root.querySelectorAll('select[data-combobox]')) {
        enhanceCombobox(select);
    }
    // Segmented controls usam radios nativos ocultos: teclado e roving tabindex
    // já vêm do navegador; nada a fazer aqui além de estilização via CSS.
}
