// Máscaras (CPF/CNPJ/data), dígitos verificadores e maiúsculas automáticas.
// CNPJ aceita alfanumérico nas 12 primeiras posições (IN RFB 2.229/2024):
// DV numérico, módulo 11 sobre (código ASCII - 48). Espelha a validação do servidor.

export function cpfStrip(value) {
    return value.replace(/\D/g, '').slice(0, 11);
}

export function cnpjStrip(value) {
    return value
        .toUpperCase()
        .replace(/[^0-9A-Z]/g, '')
        .slice(0, 14);
}

export function formatCpf(digits) {
    let out = digits.slice(0, 3);
    if (digits.length > 3) out += '.' + digits.slice(3, 6);
    if (digits.length > 6) out += '.' + digits.slice(6, 9);
    if (digits.length > 9) out += '-' + digits.slice(9, 11);
    return out;
}

export function formatCnpj(chars) {
    let out = chars.slice(0, 2);
    if (chars.length > 2) out += '.' + chars.slice(2, 5);
    if (chars.length > 5) out += '.' + chars.slice(5, 8);
    if (chars.length > 8) out += '/' + chars.slice(8, 12);
    if (chars.length > 12) out += '-' + chars.slice(12, 14);
    return out;
}

export function dateStrip(value) {
    return value.replace(/\D/g, '').slice(0, 8);
}

// dd/mm/aaaa, mesmo formato que o servidor espera (IssueForm.BR_DATE)
export function formatDate(digits) {
    let out = digits.slice(0, 2);
    if (digits.length > 2) out += '/' + digits.slice(2, 4);
    if (digits.length > 4) out += '/' + digits.slice(4, 8);
    return out;
}

export function cpfValid(digits) {
    if (!/^\d{11}$/.test(digits)) return false;
    if (/^(\d)\1{10}$/.test(digits)) return false;
    const dv = (len) => {
        let sum = 0;
        for (let i = 0; i < len; i++) sum += Number(digits[i]) * (len + 1 - i);
        const r = (sum * 10) % 11;
        return r === 10 ? 0 : r;
    };
    return dv(9) === Number(digits[9]) && dv(10) === Number(digits[10]);
}

const CNPJ_W1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
const CNPJ_W2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];

export function cnpjValid(chars) {
    if (!/^[0-9A-Z]{12}\d{2}$/.test(chars)) return false;
    if (/^(.)\1{13}$/.test(chars)) return false;
    const val = (c) => c.charCodeAt(0) - 48;
    const dv = (weights, len) => {
        let sum = 0;
        for (let i = 0; i < len; i++) sum += val(chars[i]) * weights[i];
        const r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    };
    return dv(CNPJ_W1, 12) === Number(chars[12]) && dv(CNPJ_W2, 13) === Number(chars[13]);
}

function attach(input, strip, format) {
    const reformat = () => {
        const stripped = strip(input.value);
        input.value = format(stripped);
        // caret sempre ao fim: máscara de documento é preenchida linearmente
        input.setSelectionRange(input.value.length, input.value.length);
    };
    input.addEventListener('input', reformat);
    input.addEventListener('blur', reformat);
}

// Maiúsculas de verdade no valor (não só visual), preservando o cursor.
// O 'change' cobre preenchimentos programáticos (dados fictícios).
function attachUppercase(input) {
    const upper = () => {
        const next = input.value.toUpperCase();
        if (next === input.value) return;
        const {selectionStart, selectionEnd} = input;
        input.value = next;
        input.setSelectionRange(selectionStart, selectionEnd);
    };
    input.addEventListener('input', upper);
    input.addEventListener('change', upper);
}

export function initMasks(root = document) {
    for (const el of root.querySelectorAll('[data-mask="cpf"]')) {
        attach(el, cpfStrip, formatCpf);
    }
    for (const el of root.querySelectorAll('[data-mask="cnpj"]')) {
        attach(el, cnpjStrip, formatCnpj);
    }
    for (const el of root.querySelectorAll('[data-mask="data"]')) {
        attach(el, dateStrip, formatDate);
    }
    for (const el of root.querySelectorAll('[data-uppercase]')) {
        attachUppercase(el);
    }
}
