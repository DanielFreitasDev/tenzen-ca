// Página de emissão: troca PF/PJ conforme o perfil, campos condicionais,
// validade por perfil, preenchimento com dados fictícios e validação de DV no submit.
import { cpfStrip, cpfValid, cnpjStrip, cnpjValid, formatCpf, formatCnpj } from './mask.js';
import { swapFieldsets } from './motion.js';

function selectedProfile(form) {
  return form.querySelector('[data-profile-radio]:checked');
}

function setFieldsetActive(fieldset, active, animate, other) {
  if (!fieldset) return;
  fieldset.disabled = !active; // fieldset desabilitado não entra no submit
  if (animate && other) return; // swapFieldsets cuida do hidden
  fieldset.hidden = !active;
}

function applyProfile(form, { animate = false } = {}) {
  const radio = selectedProfile(form);
  if (!radio) return;
  const holder = radio.dataset.holder;
  const legacy = radio.dataset.legacy === 'true';
  const maxYears = Number(radio.dataset.maxYears || 5);

  const pf = form.querySelector('[data-fieldset="pf"]');
  const pj = form.querySelector('[data-fieldset="pj"]');
  const showPf = holder === 'PF';
  const wasPfVisible = !pf.hidden;

  pf.disabled = !showPf;
  pj.disabled = showPf;
  if (animate && wasPfVisible !== showPf) {
    swapFieldsets(showPf ? pj : pf, showPf ? pf : pj);
  } else {
    pf.hidden = !showPf;
    pj.hidden = showPf;
  }

  // campos exclusivos do e-CPF legado (título de eleitor, CEI/NIT, domínio OU⑤)
  for (const el of form.querySelectorAll('[data-legacy-pf]')) {
    el.hidden = !(legacy && holder === 'PF');
    for (const input of el.querySelectorAll('input,select')) {
      input.disabled = el.hidden;
    }
  }
  const cityHintPf = form.querySelector('[data-city-hint-pf]');
  const cityHintPj = form.querySelector('[data-city-hint-pj]');
  if (cityHintPf) cityHintPf.hidden = holder !== 'PF';
  if (cityHintPj) cityHintPj.hidden = holder !== 'PJ';

  // aviso de A3/A4/SE-H em arquivo
  const a3Note = form.querySelector('[data-a3-note]');
  const label = radio.dataset.label || '';
  a3Note.hidden = !/A3|A4|SE-H/.test(label);

  // validade: esconde opções acima do teto do perfil
  let checkedVisible = false;
  for (const option of form.querySelectorAll('[data-anos-option]')) {
    const years = Number(option.dataset.anosOption);
    const visible = years <= maxYears;
    option.hidden = !visible;
    const input = option.querySelector('input');
    input.disabled = !visible;
    if (visible && input.checked) checkedVisible = true;
  }
  if (!checkedVisible) {
    const first = form.querySelector('[data-anos-option]:not([hidden]) input');
    if (first) first.checked = true;
  }
  const teto = form.querySelector('[data-validade-teto]');
  if (teto) teto.textContent = `Teto do perfil: ${maxYears} ${maxYears === 1 ? 'ano' : 'anos'}.`;
}

function applyValidadeMode(form) {
  const mode = form.querySelector('[data-validade-modo]:checked')?.value || 'perfil';
  for (const panel of form.querySelectorAll('[data-validade-panel]')) {
    const active = panel.dataset.validadePanel === mode;
    panel.hidden = !active;
    for (const input of panel.querySelectorAll('input')) {
      if (!active) {
        input.disabled = true;
      } else if (!input.closest('[data-anos-option]')) {
        input.disabled = false;
      }
    }
  }
  // radios de anos reabilitados conforme o teto do perfil
  if (mode === 'perfil') applyProfile(form);
}

const FIELD_MAP = {
  pessoa: {
    nome: 'nome', cpf: 'cpf', nascimento: 'nascimento', rg: 'rg',
    rgOrgaoUf: 'rgOrgaoUf', nis: 'nis', tituloEleitor: 'tituloEleitor',
    tituloZona: 'tituloZona', tituloSecao: 'tituloSecao',
    cidade: 'cidade', uf: 'uf', email: 'email',
  },
  empresa: {
    razaoSocial: 'razaoSocial', cnpj: 'cnpjField', cei: 'cei', cidade: 'cidade',
    uf: 'uf', email: 'email', responsavelNome: 'responsavelNome',
    responsavelCpf: 'responsavelCpf', responsavelNascimento: 'responsavelNascimento',
    responsavelRg: 'responsavelRg', responsavelRgOrgaoUf: 'responsavelRgOrgaoUf',
    responsavelNis: 'responsavelNis',
  },
};

async function fillRandom(form, kind) {
  const alfanumerico = new Date() >= new Date(2026, 6, 27); // IN RFB 2.229/2024
  const url = kind === 'pessoa'
    ? '/api/dados-aleatorios/pessoa'
    : `/api/dados-aleatorios/empresa?cnpjAlfanumerico=${alfanumerico}`;
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) return;
  const data = await response.json();
  for (const [key, id] of Object.entries(FIELD_MAP[kind])) {
    const input = document.getElementById(id);
    if (!input || data[key] === undefined) continue;
    let value = data[key];
    if (id === 'cpf' || id === 'responsavelCpf') value = formatCpf(cpfStrip(value));
    if (id === 'cnpjField') value = formatCnpj(cnpjStrip(value));
    input.value = value;
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }
}

function markInvalid(input, message) {
  input.setAttribute('aria-invalid', 'true');
  let error = input.parentElement.querySelector('[data-live-error]');
  if (!error) {
    error = document.createElement('p');
    error.className = 'field-error';
    error.dataset.liveError = '';
    input.parentElement.appendChild(error);
  }
  error.textContent = message;
}

function clearInvalid(input) {
  input.removeAttribute('aria-invalid');
  input.parentElement.querySelector('[data-live-error]')?.remove();
}

function validateDocuments(form) {
  let firstInvalid = null;
  const holder = selectedProfile(form)?.dataset.holder;

  const checks = holder === 'PF'
    ? [['cpf', cpfStrip, cpfValid, 'CPF inválido (dígito verificador não confere)']]
    : [
        ['cnpjField', cnpjStrip, cnpjValid, 'CNPJ inválido (dígito verificador não confere)'],
        ['responsavelCpf', cpfStrip, cpfValid, 'CPF inválido (dígito verificador não confere)'],
      ];
  for (const [id, strip, valid, message] of checks) {
    const input = document.getElementById(id);
    if (!input || input.disabled) continue;
    if (!valid(strip(input.value))) {
      markInvalid(input, message);
      firstInvalid ??= input;
    } else {
      clearInvalid(input);
    }
  }

  const senha = document.getElementById('senha');
  const confirma = document.getElementById('senhaConfirma');
  if (senha && confirma && senha.value !== confirma.value) {
    markInvalid(confirma, 'As senhas não conferem');
    firstInvalid ??= confirma;
  } else if (confirma) {
    clearInvalid(confirma);
  }
  return firstInvalid;
}

export function initIssuePage() {
  const form = document.getElementById('issue-form');
  if (!form) return;

  applyProfile(form);
  applyValidadeMode(form);

  for (const radio of form.querySelectorAll('[data-profile-radio]')) {
    radio.addEventListener('change', () => {
      applyProfile(form, { animate: true });
      applyValidadeMode(form);
    });
  }
  for (const radio of form.querySelectorAll('[data-validade-modo]')) {
    radio.addEventListener('change', () => applyValidadeMode(form));
  }
  for (const button of form.querySelectorAll('[data-random]')) {
    button.addEventListener('click', () => fillRandom(form, button.dataset.random));
  }
  form.addEventListener('submit', (event) => {
    const firstInvalid = validateDocuments(form);
    if (firstInvalid) {
      event.preventDefault();
      firstInvalid.focus();
      firstInvalid.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  });
}
