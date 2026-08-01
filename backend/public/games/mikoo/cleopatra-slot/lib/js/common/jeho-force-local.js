/* JEHO: never talk to BaiShun/jieyou — rewrite to local API. */
(function () {
  if (window.__jehoForceLocal) return;
  window.__jehoForceLocal = 1;
  var LOCAL = 'https://api.adnova.bbs.tr';
  var LOCAL_WS = 'wss://api.adnova.bbs.tr';
  var BAD = /(jieyou\.shop|sruner\.com|zkruner\.com)/i;
  function rewrite(u) {
    if (u == null) return u;
    var s = String(u);
    if (!BAD.test(s)) return s;
    try {
      var a = document.createElement('a');
      a.href = s;
      var path = (a.pathname || '/') + (a.search || '') + (a.hash || '');
      if (/^wss?:/i.test(s)) return LOCAL_WS + path;
      return LOCAL + path;
    } catch (e) {
      return s.replace(/^https?:\/\/[^/]+/i, LOCAL).replace(/^wss?:\/\/[^/]+/i, LOCAL_WS);
    }
  }
  try {
    var q = new URLSearchParams(location.search || '');
    var dom = q.get('DOMAIN') || q.get('domain');
    if (dom && !BAD.test(dom)) {
      window.__JEHO_DOMAIN = dom;
      LOCAL = String(dom).replace(/\/$/, '');
      if (/^https?:/i.test(LOCAL)) {
        LOCAL_WS = LOCAL.replace(/^https:/i, 'wss:').replace(/^http:/i, 'ws:');
      }
    }
  } catch (e) {}
  var OW = window.WebSocket;
  if (OW) {
    window.WebSocket = function (url, protocols) {
      var u = rewrite(url);
      return protocols !== undefined ? new OW(u, protocols) : new OW(u);
    };
    window.WebSocket.prototype = OW.prototype;
    window.WebSocket.CONNECTING = OW.CONNECTING;
    window.WebSocket.OPEN = OW.OPEN;
    window.WebSocket.CLOSING = OW.CLOSING;
    window.WebSocket.CLOSED = OW.CLOSED;
  }
  if (window.fetch) {
    var ofetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      if (typeof input === 'string') input = rewrite(input);
      else if (input && typeof Request !== 'undefined' && input instanceof Request) {
        input = new Request(rewrite(input.url), input);
      }
      return ofetch(input, init);
    };
  }
  var XO = window.XMLHttpRequest;
  if (XO && XO.prototype) {
    var open = XO.prototype.open;
    XO.prototype.open = function () {
      var args = Array.prototype.slice.call(arguments);
      if (args.length > 1) args[1] = rewrite(args[1]);
      return open.apply(this, args);
    };
  }
})();
