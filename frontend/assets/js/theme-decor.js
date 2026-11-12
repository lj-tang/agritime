/**
 * 主题装饰层：按当前 data-theme 注入对应场景 SVG。
 *   rice(稻花)     -> light / paddy
 *   wheat(麦穗)    -> wheat
 *   bloom(油菜花)  -> rapeseed
 *   terrace(梯田)  -> terra
 *   smoke(炊烟)    -> barn
 *   windmill(风车) -> 备用（旧主题 ocean/forest 迁移时的过渡）
 *   town(村镇)     -> dark / amoled
 * 纯 CSS 着色 + 动画，跟随主题切换实时重绘。
 */
(function () {
  var WINDMILL = '<svg class="deco-svg deco-windmill" viewBox="0 0 220 240" aria-hidden="true">'
    + '<path class="deco-ground" d="M0 234 Q64 210 140 230 T220 234 L220 240 L0 240 Z"/>'
    + '<path class="deco-tower" d="M96 108 L124 108 L134 236 L86 236 Z"/>'
    + '<g class="deco-spin" style="transform-box:view-box;transform-origin:110px 100px;">'
    + '<path class="deco-blade" d="M110 94 L110 26 L128 92 Z"/>'
    + '<path class="deco-blade" d="M116 100 L184 100 L118 118 Z"/>'
    + '<path class="deco-blade" d="M110 106 L110 174 L92 108 Z"/>'
    + '<path class="deco-blade" d="M104 100 L36 100 L102 82 Z"/>'
    + '</g>'
    + '<circle class="deco-hub" cx="110" cy="100" r="9"/>'
    + '</svg>';

  var RICE = '<svg class="deco-svg deco-rice" viewBox="0 0 200 220" aria-hidden="true">'
    + '<g class="deco-sway" style="transform-box:view-box;transform-origin:100px 214px;">'
    + '<path class="deco-stem" d="M100 214 Q104 150 96 70"/>'
    + '<g class="deco-grain">'
    + '<ellipse cx="96" cy="70" rx="6" ry="13"/>'
    + '<ellipse cx="80" cy="84" rx="6" ry="13" transform="rotate(-24 80 84)"/>'
    + '<ellipse cx="112" cy="84" rx="6" ry="13" transform="rotate(24 112 84)"/>'
    + '<ellipse cx="74" cy="102" rx="6" ry="13" transform="rotate(-30 74 102)"/>'
    + '<ellipse cx="118" cy="102" rx="6" ry="13" transform="rotate(30 118 102)"/>'
    + '<ellipse cx="70" cy="122" rx="6" ry="13" transform="rotate(-34 70 122)"/>'
    + '<ellipse cx="122" cy="122" rx="6" ry="13" transform="rotate(34 122 122)"/>'
    + '</g>'
    + '<path class="deco-leaf" d="M100 182 Q68 170 58 140 Q84 154 100 174 Z"/>'
    + '<path class="deco-leaf" d="M100 172 Q132 158 144 128 Q116 144 100 164 Z"/>'
    + '</g>'
    + '</svg>';

  var SMOKE = '<svg class="deco-svg deco-smoke" viewBox="0 0 160 200" aria-hidden="true">'
    + '<path class="deco-smoke-line deco-smoke-a" d="M70 190 C58 150 88 128 70 92 C58 66 84 44 72 14"/>'
    + '<path class="deco-smoke-line deco-smoke-b" d="M92 190 C104 154 80 132 96 100 C110 72 90 48 98 20"/>'
    + '<path class="deco-house" d="M40 200 L40 162 L70 138 L100 162 L100 200 Z"/>'
    + '<path class="deco-house" d="M34 164 L70 132 L106 164" fill="none" stroke-width="2"/>'
    + '</svg>';

  var TOWN = '<svg class="deco-svg deco-town" viewBox="0 0 420 160" preserveAspectRatio="none" aria-hidden="true">'
    + '<path d="M0 160 L0 104 L26 104 L26 78 L52 78 L52 104 L78 104 L78 54 L98 34 L118 54 L118 104 L148 104 L148 82 L172 82 L172 104 L198 104 L198 44 L214 26 L230 44 L230 104 L256 104 L256 76 L280 76 L280 104 L312 104 L312 58 L338 58 L338 104 L372 104 L372 88 L420 88 L420 160 Z"/>'
    + '<g>'
    + '<rect class="deco-window deco-window--twinkle" x="88" y="70" width="6" height="8"/>'
    + '<rect class="deco-window" x="106" y="70" width="6" height="8"/>'
    + '<rect class="deco-window deco-window--twinkle" x="206" y="62" width="6" height="8"/>'
    + '<rect class="deco-window" x="220" y="62" width="6" height="8"/>'
    + '<rect class="deco-window deco-window--twinkle" x="320" y="74" width="6" height="8"/>'
    + '<rect class="deco-window" x="336" y="74" width="6" height="8"/>'
    + '</g>'
    + '</svg>';

  // 麦穗：右下角，两穗沉甸甸地随风扇动（麦浪金黄主题）
  var WHEAT = '<svg class="deco-svg deco-wheat" viewBox="0 0 200 230" aria-hidden="true">'
    + '<g class="deco-sway" style="transform-box:view-box;transform-origin:100px 226px;">'
    + '<path class="deco-stem" d="M74 226 Q78 150 70 48"/>'
    + '<g class="deco-ear">'
    + '<ellipse cx="70" cy="22" rx="5" ry="11"/>'
    + '<ellipse cx="63" cy="32" rx="4.4" ry="9" transform="rotate(-24 63 32)"/>'
    + '<ellipse cx="77" cy="32" rx="4.4" ry="9" transform="rotate(24 77 32)"/>'
    + '<ellipse cx="64" cy="45" rx="4" ry="8" transform="rotate(-24 64 45)"/>'
    + '<ellipse cx="76" cy="45" rx="4" ry="8" transform="rotate(24 76 45)"/>'
    + '</g>'
    + '<path class="deco-awn" d="M70 10 L67 -6 M70 10 L73 -7"/>'
    + '<path class="deco-stem" d="M126 226 Q122 150 130 76"/>'
    + '<g class="deco-ear">'
    + '<ellipse cx="130" cy="52" rx="4.6" ry="10"/>'
    + '<ellipse cx="124" cy="61" rx="4" ry="8.4" transform="rotate(-24 124 61)"/>'
    + '<ellipse cx="136" cy="61" rx="4" ry="8.4" transform="rotate(24 136 61)"/>'
    + '<ellipse cx="125" cy="73" rx="3.7" ry="7.4" transform="rotate(-24 125 73)"/>'
    + '<ellipse cx="135" cy="73" rx="3.7" ry="7.4" transform="rotate(24 135 73)"/>'
    + '</g>'
    + '<path class="deco-awn" d="M130 41 L128 26 M130 41 L133 25"/>'
    + '</g>'
    + '</svg>';

  // 油菜花：右下角，四瓣小花（油菜花田主题）
  var BLOOM = '<svg class="deco-svg deco-bloom" viewBox="0 0 200 210" aria-hidden="true">'
    + '<g class="deco-sway" style="transform-box:view-box;transform-origin:100px 206px;">'
    + '<path class="deco-stem" d="M100 206 Q104 150 96 84"/>'
    + '<path class="deco-stem" d="M64 206 Q68 168 60 132"/>'
    + '<path class="deco-stem" d="M138 206 Q134 168 142 132"/>'
    + '<g class="deco-flower">'
    + '<circle cx="96" cy="70" r="12"/><circle cx="84" cy="82" r="12"/>'
    + '<circle cx="108" cy="82" r="12"/><circle cx="96" cy="94" r="12"/>'
    + '</g><circle class="deco-flower-core" cx="96" cy="82" r="5.5"/>'
    + '<g class="deco-flower">'
    + '<circle cx="60" cy="120" r="9"/><circle cx="51" cy="129" r="9"/>'
    + '<circle cx="69" cy="129" r="9"/><circle cx="60" cy="138" r="9"/>'
    + '</g><circle class="deco-flower-core" cx="60" cy="129" r="4"/>'
    + '<g class="deco-flower">'
    + '<circle cx="142" cy="120" r="9"/><circle cx="133" cy="129" r="9"/>'
    + '<circle cx="151" cy="129" r="9"/><circle cx="142" cy="138" r="9"/>'
    + '</g><circle class="deco-flower-core" cx="142" cy="129" r="4"/>'
    + '<path class="deco-leaf" d="M100 152 Q74 142 64 112 Q90 124 100 146 Z"/>'
    + '<path class="deco-leaf" d="M100 140 Q128 128 140 96 Q112 112 100 132 Z"/>'
    + '</g>'
    + '</svg>';

  // 梯田：底部一层层错开的水面反光（红壤梯田主题）
  var TERRACE = '<svg class="deco-svg deco-terrace" viewBox="0 0 600 180" preserveAspectRatio="none" aria-hidden="true">'
    + '<path class="deco-step deco-step-1" d="M0 96 Q150 76 300 94 T600 86 L600 120 L0 120 Z"/>'
    + '<path class="deco-step deco-step-2" d="M0 122 Q150 102 300 120 T600 112 L600 148 L0 148 Z"/>'
    + '<path class="deco-step deco-step-3" d="M0 150 Q150 130 300 148 T600 140 L600 180 L0 180 Z"/>'
    + '</svg>';

  var SCENES = {
    windmill: WINDMILL, rice: RICE, smoke: SMOKE, town: TOWN,
    wheat: WHEAT, bloom: BLOOM, terrace: TERRACE
  };
  var MAP = {
    light: 'rice', paddy: 'rice',
    wheat: 'wheat', rapeseed: 'bloom', terra: 'terrace', barn: 'smoke',
    dark: 'town', amoled: 'town',
    // 旧主题 id：theme.js 会迁移偏好，但首帧那一瞬 data-theme 可能还是老值
    ocean: 'windmill', forest: 'windmill', sunset: 'rice', sepia: 'smoke'
  };

  var layer = document.createElement('div');
  layer.className = 'theme-decor';
  layer.setAttribute('aria-hidden', 'true');

  function themeKey() {
    return document.documentElement.getAttribute('data-theme') || 'light';
  }

  function render() {
    var t = themeKey();
    var scene = MAP[t] || 'rice';
    layer.setAttribute('data-scene', scene);
    layer.innerHTML = SCENES[scene] || '';
  }

  function attach() {
    if (!document.body) return;
    if (!document.body.contains(layer)) document.body.insertBefore(layer, document.body.firstChild);
    render();
  }

  if (document.body) attach();
  else document.addEventListener('DOMContentLoaded', attach);

  try {
    new MutationObserver(function () {
      if (document.body && document.body.contains(layer)) render();
    }).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  } catch (e) { /* 老浏览器忽略 */ }

  window.ThemeDecor = { render: render };
})();