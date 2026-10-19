'use strict';

// All runtime paths resolve to files inside the APK. This module only typesets;
// it never evaluates an expression or implements any CAS operation.
window.MathJax = {
  loader: {paths: {mathjax: 'https://axioma.invalid/renderer/vendor/mathjax',
    fonts: 'https://axioma.invalid/renderer/vendor/fonts'}},
  tex: {packages: {'[-]': ['noundefined', 'autoload', 'require', 'configmacros']}, maxMacros: 1000, maxBuffer: 16384,
    formatError(_jax, error) { throw error; }},
  output: {font: 'mathjax-newcm', fontPath: '[fonts]/%%FONT%%-font'},
  svg: {fontCache: 'local'},
  options: {enableMenu: false, enableExplorer: false, enableEnrichment: false,
    enableSpeech: false, enableBraille: false,
    menuOptions: {settings: {enrich: false, speech: false, braille: false,
      collapsible: false, assistiveMml: true}}},
  startup: {typeset: false}
};

window.Axioma = (() => {
  let latest = 0;
  let queue = Promise.resolve();
  let lastSize = '';
  let success = true;
  let completed = -1;
  let viewportWidth = 0;
  const target = () => document.getElementById('formula');
  const viewport = () => document.getElementById('viewport');
  function followEnd() {
    const area = viewport();
    if (!area) return;
    const overflow = Math.max(0, area.scrollWidth - area.clientWidth);
    area.style.overflowX = overflow > 2 ? 'auto' : 'hidden';
    // The last leaf is the denominator in a fraction, or the final digit/operator
    // in ordinary input. Keep it visible without moving the separate result row.
    const leaves = area.querySelectorAll('svg [data-mml-node="mn"], svg [data-mml-node="mi"], svg [data-mml-node="mo"]');
    const leaf = leaves.length ? leaves[leaves.length - 1] : null;
    const desired = leaf ? area.scrollLeft + leaf.getBoundingClientRect().right
      - area.getBoundingClientRect().right + 8 : overflow;
    area.scrollLeft = overflow > 2 ? Math.max(0, Math.min(overflow, desired)) : 0;
  }
  function fitDisplay() {
    const area = viewport();
    if (!area) return;
    const available = area.clientWidth;
    if (available <= 0) return;
    viewportWidth = available;
    const expression = document.getElementById('expression');
    const finalResult = expression?.dataset.completed === 'true';
    let expressionScale = 1;
    if (expression) {
      expression.style.fontSize = '1em';
      const natural = expression.querySelector('svg').getBoundingClientRect().width;
      // Preserve a readable floor: 20 relative to the normal 30-unit formula size.
      expressionScale = natural > 0 ? Math.max(2 / 3, Math.min(1, (available - 2) / natural)) : 1;
      expression.style.fontSize = `${expressionScale * (finalResult ? 0.5 : 1)}em`;
    }
    const answer = document.getElementById('answer');
    if (answer) {
      // Equals swaps the hierarchy; use the editing scale before shrinking input.
      const size = expressionScale * (finalResult ? 1 : 0.5);
      answer.style.fontSize = `${size}em`;
      const width = answer.getBoundingClientRect().width;
      if (width > available) answer.style.fontSize = `${size * (available - 2) / width}em`;
    }
  }
  function report() {
    if (completed !== latest) return;
    const height = Math.ceil(target().getBoundingClientRect().height);
    const key = `${latest}:${height}:${success}`;
    if (key !== lastSize) {
      lastSize = key;
      window.AxiomaHost.rendered(latest, height, success);
    }
  }
  return {
    start() {
      MathJax.startup.promise.then(() => {
        new ResizeObserver(report).observe(target());
        window.addEventListener('resize', () => {
          if (completed !== latest || !viewport() || viewport().clientWidth === viewportWidth) return;
          fitDisplay(); followEnd(); report();
        });
        window.AxiomaHost.ready();
      }).catch(() => {
        success = false;
        target().textContent = 'Formula renderer could not start.'; completed = latest;
        report();
      });
    },
    configure(color, size) {
      document.body.style.color = color;
      document.body.style.fontSize = `${size}px`;
    },
    render(latex, generation, answer = '', display = '', finalResult = false, source = '', diagnostic = false, exact = false) {
      latest = generation;
      if (!latex) {
        target().replaceChildren(); target().dataset.latex = ''; target().dataset.result = '';
        target().dataset.source = ''; viewportWidth = 0;
        success = true; completed = generation; report(); return;
      }
      queue = queue.catch(() => {}).then(async () => {
        if (generation !== latest) return;
        try {
          const node = await MathJax.tex2svgPromise(latex, {display: true});
          if (generation !== latest) return;
          if (!node.querySelector('svg') || node.querySelector('[data-mjx-error], [data-mml-node="merror"]'))
            throw new Error('Invalid formula.');
          const expression = document.createElement('div');
          expression.id = 'expression'; expression.appendChild(node);
          expression.dataset.completed = String(finalResult && !!answer);
          expression.setAttribute('aria-label', source);
          expression.onclick = () => window.AxiomaHost.edit();
          const area = document.createElement('div'); area.id = 'viewport'; area.appendChild(expression);
          const fragment = document.createDocumentFragment(); fragment.appendChild(area);
          if (answer) {
            const result = document.createElement('div'); result.id = 'answer';
            result.style.fontSize = '0.5em';
            result.style.opacity = finalResult ? '1' : '0.75';
            result.setAttribute('aria-label', `${diagnostic ? 'Result' : exact ? 'Exact result' : 'Approximate result'}: ${display}`);
            const rendered = await MathJax.tex2svgPromise(answer, {display: true});
            if (generation !== latest) return;
            result.appendChild(rendered); fragment.appendChild(result);
          }
          target().replaceChildren(fragment);
          fitDisplay(); followEnd();
          target().className = '';
          target().dataset.latex = latex;
          target().dataset.result = display;
          target().dataset.source = source;
          success = true;
        } catch (_) {
          if (generation !== latest) return;
          target().textContent = latex;
          target().className = 'render-error'; success = false;
        }
        completed = generation; report();
      });
    },
    glyph(tex) {
      queue = queue.catch(() => {}).then(async () => {
        const host = document.getElementById('glyph-work');
        try {
          const node = await MathJax.tex2svgPromise(tex, {display: true});
          host.replaceChildren(node);
          const original = node.querySelector('svg');
          if (!original || node.querySelector('[data-mml-node="merror"]')) throw new Error('Invalid label.');
          const bounds = original.getBoundingClientRect();
          const ratio = window.devicePixelRatio;
          const reduction = Math.min(1, 4096 / Math.max(1, bounds.width * ratio),
            1024 / Math.max(1, bounds.height * ratio));
          const width = Math.max(1, Math.ceil(bounds.width * ratio * reduction));
          const height = Math.max(1, Math.ceil(bounds.height * ratio * reduction));
          const svg = original.cloneNode(true);
          svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
          svg.setAttribute('width', width); svg.setAttribute('height', height);
          svg.setAttribute('color', '#ffffff'); svg.style.color = '#ffffff';
          const image = new Image();
          await new Promise((resolve, reject) => {
            image.onload = resolve; image.onerror = reject;
            image.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(new XMLSerializer().serializeToString(svg));
          });
          const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
          canvas.getContext('2d').drawImage(image, 0, 0, width, height);
          window.AxiomaHost.glyph(tex, canvas.toDataURL('image/png').split(',')[1], 64 * ratio * reduction);
        } catch (_) { window.AxiomaHost.glyphFailed(tex); }
        host.replaceChildren();
      });
    }
  };
})();
