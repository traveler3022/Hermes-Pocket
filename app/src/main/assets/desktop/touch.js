// Touch layer for the noVNC page: turns phone gestures into the mouse and keyboard events
// noVNC listens for. Loaded by LinuxDesktopViewerScreen after every page load.
//
//   pad mode    one finger moves the pointer like a laptop touchpad, tap = click
//   touch mode  the pointer jumps to the finger, which presses and drags
//   both        two fingers scroll, pinch zooms (then pans), two-finger tap = right click,
//               three fingers drag with the button held
(() => {
  const MAX_ZOOM = 4;
  const MOVE_SLOP = 8;
  const PINCH_SLOP = 24;
  const TAP_MS = 450;
  // Chromium's own toolbar in the phone layout; a tap there selects the address to retype it.
  const ADDRESS_BAR = [34, 100];

  const head = document.head;
  let meta = document.querySelector('meta[name="viewport"]');
  if (!meta) {
    meta = document.createElement('meta');
    meta.name = 'viewport';
    head.prepend(meta);
  }
  meta.content = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';

  if (!document.getElementById('hermes-desk-style')) {
    const style = document.createElement('style');
    style.id = 'hermes-desk-style';
    style.textContent = [
      'html, body { margin: 0 !important; width: 100% !important; overflow: hidden !important; overscroll-behavior: none !important; }',
      '#top_bar { box-sizing: border-box !important; min-height: 24px !important; flex: 0 0 24px !important; padding: 4px 8px !important; font: 500 10px/16px sans-serif !important; }',
      '#status { display: block !important; overflow: hidden !important; white-space: nowrap !important; text-overflow: ellipsis !important; }',
      '#sendCtrlAltDelButton { display: none !important; }',
      '#screen { min-width: 0 !important; min-height: 0 !important; overflow: hidden !important; }',
      '#screen, #screen canvas { touch-action: none !important; }',
      // noVNC draws the remote cursor on its own canvas at device pixels; keep it cursor-sized.
      'body > canvas[style*="z-index: 65535"] { transform: scale(var(--desk-cursor)) !important; transform-origin: 0 0 !important; }',
    ].join('\n');
    head.appendChild(style);
  }
  document.documentElement.style.setProperty('--desk-cursor', String(1 / Math.max(1, devicePixelRatio)));

  const fitHeight = () => {
    const height = innerHeight + 'px';
    document.documentElement.style.height = height;
    document.body.style.height = height;
    dispatchEvent(new Event('resize'));
  };

  const clamp = (value, low, high) => Math.min(high, Math.max(low, value));

  // Collects deltas and applies them at most once per frame.
  const perFrame = apply => {
    let dx = 0;
    let dy = 0;
    let frame = 0;
    const flush = () => {
      if (frame) cancelAnimationFrame(frame);
      frame = 0;
      if (!dx && !dy) return;
      const x = dx;
      const y = dy;
      dx = 0;
      dy = 0;
      apply(x, y);
    };
    return {
      add(x, y) {
        dx += x;
        dy += y;
        if (!frame) frame = requestAnimationFrame(() => { frame = 0; flush(); });
      },
      flush,
      drop() {
        if (frame) cancelAnimationFrame(frame);
        frame = 0;
        dx = 0;
        dy = 0;
      },
    };
  };

  const attach = canvas => {
    let mode = window.hermesDeskMode === 'touch' ? 'touch' : 'pad';
    const pointer = { x: canvas.width / 2, y: canvas.height / 2 };
    const view = { scale: 1, x: 0, y: 0 };
    let holding = false;
    let gesture = null;

    const box = () => canvas.getBoundingClientRect();

    const pointerOnPage = () => {
      const r = box();
      return {
        x: r.left + pointer.x * r.width / Math.max(1, canvas.width),
        y: r.top + pointer.y * r.height / Math.max(1, canvas.height),
      };
    };

    const pointTo = (clientX, clientY) => {
      const r = box();
      pointer.x = clamp((clientX - r.left) * canvas.width / Math.max(1, r.width), 0, canvas.width - 1);
      pointer.y = clamp((clientY - r.top) * canvas.height / Math.max(1, r.height), 0, canvas.height - 1);
    };

    const mouse = (type, buttons, target = canvas) => {
      const at = pointerOnPage();
      target.dispatchEvent(new MouseEvent(type, {
        bubbles: true, cancelable: true, clientX: at.x, clientY: at.y, button: 0, buttons,
      }));
    };

    const nudge = (dx, dy) => {
      const r = box();
      pointer.x = clamp(pointer.x + dx * canvas.width / Math.max(1, r.width), 0, canvas.width - 1);
      pointer.y = clamp(pointer.y + dy * canvas.height / Math.max(1, r.height), 0, canvas.height - 1);
      mouse('mousemove', holding ? 1 : 0);
    };

    const moves = perFrame(nudge);
    const scrolls = perFrame((dx, dy) => {
      const at = pointerOnPage();
      canvas.dispatchEvent(new WheelEvent('wheel', {
        bubbles: true, cancelable: true, clientX: at.x, clientY: at.y,
        deltaX: -2 * dx, deltaY: -2 * dy, deltaMode: 0,
      }));
    });

    const press = () => {
      if (holding) return;
      mouse('mousemove', 0);
      mouse('mousedown', 1);
      holding = true;
    };

    // While a button is down noVNC captures the mouse onto a proxy element; the release
    // has to land there or the button stays stuck.
    const release = (force = false) => {
      if (!holding && !force) return;
      moves.flush();
      const proxy = document.captureElement && document.getElementById('noVNC_mouse_capture_elem');
      mouse('mouseup', 0, proxy || canvas);
      holding = false;
    };

    const rightClick = () => {
      const at = pointerOnPage();
      canvas.dispatchEvent(new CustomEvent('gesturestart', {
        bubbles: true, cancelable: true, detail: { type: 'twotap', clientX: at.x, clientY: at.y },
      }));
    };

    const paint = () => {
      canvas.style.transformOrigin = '0 0';
      canvas.style.transform = `translate(${view.x}px, ${view.y}px) scale(${view.scale})`;
    };

    // The zoomed canvas must keep covering its 1x area: no empty margins to pan into.
    const keepCovered = () => {
      view.x = clamp(view.x, -canvas.offsetWidth * (view.scale - 1), 0);
      view.y = clamp(view.y, -canvas.offsetHeight * (view.scale - 1), 0);
    };

    const zoomAround = (scale, anchorX, anchorY) => {
      const next = clamp(scale, 1, MAX_ZOOM);
      if (Math.abs(next - view.scale) < 0.001) return;
      const r = box();
      const originX = r.left - view.x;
      const originY = r.top - view.y;
      const contentX = (anchorX - r.left) / view.scale;
      const contentY = (anchorY - r.top) / view.scale;
      view.scale = next;
      view.x = anchorX - originX - contentX * next;
      view.y = anchorY - originY - contentY * next;
      keepCovered();
      paint();
    };

    const pan = (dx, dy) => {
      view.x += dx;
      view.y += dy;
      keepCovered();
      paint();
    };

    const NAMED_KEYS = {
      Backspace: ['Backspace', 8],
      Tab: ['Tab', 9],
      Enter: ['Enter', 13],
      Escape: ['Escape', 27],
      ArrowLeft: ['ArrowLeft', 37],
      ArrowUp: ['ArrowUp', 38],
      ArrowRight: ['ArrowRight', 39],
      ArrowDown: ['ArrowDown', 40],
      Delete: ['Delete', 46],
    };

    const keyEvent = (type, key, code, keyCode) => canvas.dispatchEvent(new KeyboardEvent(type, {
      key, code, keyCode, which: keyCode, bubbles: true, cancelable: true,
    }));

    const stroke = (key, code, keyCode) => {
      keyEvent('keydown', key, code, keyCode);
      keyEvent('keyup', key, code, keyCode);
    };

    const key = name => {
      const named = NAMED_KEYS[name];
      if (named) {
        stroke(name, named[0], named[1]);
      } else {
        stroke(name, 'Unidentified', name.length === 1 ? name.toUpperCase().charCodeAt(0) : 0);
      }
    };

    const selectAll = () => {
      keyEvent('keydown', 'Control', 'ControlLeft', 17);
      stroke('a', 'KeyA', 65);
      keyEvent('keyup', 'Control', 'ControlLeft', 17);
    };

    const center = touches => {
      let x = 0;
      let y = 0;
      for (const t of touches) {
        x += t.clientX;
        y += t.clientY;
      }
      const n = Math.max(1, touches.length);
      return { x: x / n, y: y / n };
    };

    const spread = touches => touches.length < 2 ? 0 :
      Math.hypot(touches[0].clientX - touches[1].clientX, touches[0].clientY - touches[1].clientY);

    const onCanvas = at => {
      const r = box();
      return at.x >= r.left && at.x <= r.right && at.y >= r.top && at.y <= r.bottom;
    };

    // Our gestures only: noVNC's own touch handling must not see them.
    const swallow = event => {
      event.preventDefault();
      event.stopImmediatePropagation();
    };

    const reset = () => {
      moves.drop();
      scrolls.drop();
      release();
      gesture = null;
    };

    const onStart = event => {
      const at = center(event.touches);
      if (!gesture) {
        if (!onCanvas(at)) return;
        if (document.captureElement) release(true);
        gesture = { fingers: 0, startedAt: performance.now(), origin: at, last: at, moved: false, spread: 0, pinching: false };
      }
      swallow(event);
      gesture.fingers = Math.max(gesture.fingers, event.touches.length);
      gesture.origin = at;
      gesture.last = at;
      if (event.touches.length === 2) {
        gesture.spread = spread(event.touches);
        gesture.pinching = false;
        release();
      } else if (event.touches.length === 1 && mode === 'touch') {
        pointTo(at.x, at.y);
        press();
      }
    };

    const onMove = event => {
      if (!gesture) return;
      swallow(event);
      const at = center(event.touches);
      const dx = at.x - gesture.last.x;
      const dy = at.y - gesture.last.y;
      const fingers = event.touches.length;
      if (Math.hypot(at.x - gesture.origin.x, at.y - gesture.origin.y) > MOVE_SLOP) gesture.moved = true;

      if (gesture.fingers === 1 && fingers === 1) {
        if (mode === 'touch') {
          pointTo(at.x, at.y);
          mouse('mousemove', holding ? 1 : 0);
        } else {
          moves.add(dx, dy);
        }
      } else if (gesture.fingers === 2 && fingers === 2) {
        const now = spread(event.touches);
        if (!gesture.spread) gesture.spread = now;
        if (gesture.pinching || Math.abs(now - gesture.spread) > PINCH_SLOP) {
          gesture.pinching = true;
          zoomAround(view.scale * now / Math.max(1, gesture.spread), at.x, at.y);
          gesture.spread = now;
        } else if (view.scale > 1) {
          pan(dx, dy);
        } else {
          scrolls.add(dx, dy);
        }
      } else if (gesture.fingers === 3 && fingers === 3 && gesture.moved) {
        press();
        moves.add(dx, dy);
      }
      gesture.last = at;
    };

    const onEnd = event => {
      if (!gesture) return;
      swallow(event);
      if (holding && event.touches.length < 3) release();
      if (event.touches.length > 0) {
        gesture.last = center(event.touches);
        return;
      }
      moves.flush();
      scrolls.flush();
      const quick = !gesture.moved && performance.now() - gesture.startedAt <= TAP_MS;
      if (quick && gesture.fingers === 1) {
        // In touch mode the finger already pressed and released the button.
        if (mode === 'pad') {
          press();
          release();
        }
        if (pointer.y >= ADDRESS_BAR[0] && pointer.y <= ADDRESS_BAR[1]) setTimeout(selectAll, 40);
        if (window.HermesDeskBridge) window.HermesDeskBridge.onClick(Math.round(pointer.x), Math.round(pointer.y));
      } else if (quick && gesture.fingers === 2 && !gesture.pinching) {
        rightClick();
      }
      gesture = null;
    };

    const onCancel = event => {
      if (!gesture) return;
      swallow(event);
      reset();
    };

    const options = { capture: true, passive: false };
    document.addEventListener('touchstart', onStart, options);
    document.addEventListener('touchmove', onMove, options);
    document.addEventListener('touchend', onEnd, options);
    document.addEventListener('touchcancel', onCancel, options);
    addEventListener('blur', reset);

    window.hermesDesk = {
      setMode(next) {
        release();
        mode = next === 'touch' ? 'touch' : 'pad';
        window.hermesDeskMode = mode;
      },
      key,
      type(text) {
        for (const ch of text) key(ch === '\n' ? 'Enter' : ch);
      },
    };
  };

  // noVNC creates its canvas once the connection is up; wait for it with real pixels.
  const tryAttach = () => {
    const canvas = document.querySelector('#screen canvas');
    if (!canvas || canvas.width < 1 || canvas.height < 1) return false;
    if (canvas.dataset.hermesDesk !== 'on') {
      canvas.dataset.hermesDesk = 'on';
      attach(canvas);
    }
    return true;
  };

  requestAnimationFrame(() => requestAnimationFrame(() => {
    fitHeight();
    if (tryAttach()) return;
    const timer = setInterval(() => { if (tryAttach()) clearInterval(timer); }, 100);
  }));
})();
