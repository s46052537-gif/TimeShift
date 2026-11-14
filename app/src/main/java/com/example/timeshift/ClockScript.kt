package com.example.timeshift

/**
 * JavaScript injected into every page before its own scripts run.
 * It replaces the page's clock: Date, performance.now, setTimeout, setInterval
 * and requestAnimationFrame all run on a "virtual" clock that can be sped up
 * and jumped forward. Only the page's local clock changes; a server never sees it.
 */
object ClockScript {
    private const val TEMPLATE = """
(function () {
  if (window.__tmInstalled) return;
  window.__tmInstalled = true;

  var OD = Date;
  var rNow = OD.now.bind(OD);
  var pNow = performance.now.bind(performance);
  var st = {
    speed: __SPEED__,
    rb: rNow(),
    vb: rNow() + __OFFSET__,
    pb: pNow(),
    pv: pNow()
  };

  function vNow() { return st.vb + (rNow() - st.rb) * st.speed; }
  function vPerf() { return st.pv + (pNow() - st.pb) * st.speed; }

  function FD() {
    if (!new.target) return new OD(vNow()).toString();
    var args = arguments.length ? Array.prototype.slice.call(arguments) : [vNow()];
    return Reflect.construct(OD, args, new.target);
  }
  FD.prototype = OD.prototype;
  FD.now = vNow;
  FD.parse = OD.parse;
  FD.UTC = OD.UTC;
  try { Object.defineProperty(FD, 'name', { value: 'Date' }); } catch (e) {}
  try { OD.prototype.constructor = FD; } catch (e) {}
  window.Date = FD;

  try { performance.now = vPerf; } catch (e) {}

  var sT = window.setTimeout.bind(window);
  var sI = window.setInterval.bind(window);
  window.setTimeout = function (fn, d) {
    var rest = Array.prototype.slice.call(arguments, 2);
    return sT.apply(null, [fn, (d || 0) / st.speed].concat(rest));
  };
  window.setInterval = function (fn, d) {
    var rest = Array.prototype.slice.call(arguments, 2);
    return sI.apply(null, [fn, Math.max((d || 0) / st.speed, 1)].concat(rest));
  };

  var raf = window.requestAnimationFrame && window.requestAnimationFrame.bind(window);
  if (raf) {
    window.requestAnimationFrame = function (cb) {
      return raf(function () { cb(vPerf()); });
    };
  }

  window.__tmSet = function (speed, jumpMs) {
    var c = vNow(), p = vPerf();
    st.rb = rNow(); st.vb = c + jumpMs;
    st.pb = pNow(); st.pv = p;
    st.speed = speed;
  };
  window.__tmReset = function () { window.__tmSet(1, 0); st.vb = rNow(); };
  window.__tmState = function () {
    return JSON.stringify({ speed: st.speed, pageTime: new OD(vNow()).toISOString() });
  };
})();
"""

    fun build(speed: Double, offsetMs: Long): String =
        TEMPLATE.replace("__SPEED__", speed.toString()).replace("__OFFSET__", offsetMs.toString())
}
