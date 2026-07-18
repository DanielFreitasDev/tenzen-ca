import {initTheme} from './theme.js';
import {initControls} from './controls.js';
import {initMasks} from './mask.js';
import {initIssuePage} from './issue.js';
import {initMotion} from './motion.js';

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initControls();
    initMasks();
    initIssuePage();
    initMotion();
});
