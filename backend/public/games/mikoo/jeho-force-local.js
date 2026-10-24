/* JEHO: route bundled Mikoo games to the active backend, never retired provider hosts. */
(function () {
  if (window.__jehoForceLocal) return;
  window.__jehoForceLocal = true;

  var LOCAL = (window.location && window.location.origin) || '';
  var LOCAL_WS = LOCAL.replace(/^https:/i, 'wss:').replace(/^http:/i, 'ws:');
  var BAD = /(api\.adnova\.bbs\.tr|jieyou\.shop|sruner\.com|zkruner\.com)/i;

  try {
    var q = new URLSearchParams(window.location.search || '');
    var requested = q.get('DOMAIN') || q.get('domain');
    if (requested && !BAD.test(requested) && /^https?:\/\//i.test(requested)) {
      LOCAL = String(requested).replace(/\/$/, '');
      LOCAL_WS = LOCAL.replace(/^https:/i, 'wss:').replace(/^http:/i, 'ws:');
    }
  } catch (e) {}

  function rewrite(url) {
    if (url == null) return url;
    var value = String(url);
    if (!BAD.test(value)) return value;
    try {
      var parsed = document.createElement('a');
      parsed.href = value;
      var path = (parsed.pathname || '/') + (parsed.search || '') + (parsed.hash || '');
      return /^wss?:/i.test(value) ? LOCAL_WS + path : LOCAL + path;
    } catch (e) {
      return value.replace(/^https?:\/\/[^/]+/i, LOCAL).replace(/^wss?:\/\/[^/]+/i, LOCAL_WS);
    }
  }

  var OriginalWebSocket = window.WebSocket;
  if (OriginalWebSocket) {
    window.WebSocket = function (url, protocols) {
      var target = rewrite(url);
      return protocols !== undefined ? new OriginalWebSocket(target, protocols) : new OriginalWebSocket(target);
    };
    window.WebSocket.prototype = OriginalWebSocket.prototype;
    ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach(function (key) {
      window.WebSocket[key] = OriginalWebSocket[key];
    });
  }

  if (window.fetch) {
    var originalFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      if (typeof input === 'string') input = rewrite(input);
      else if (input && typeof Request !== 'undefined' && input instanceof Request) {
        input = new Request(rewrite(input.url), input);
      }
      return originalFetch(input, init);
    };
  }

  var OriginalXHR = window.XMLHttpRequest;
  if (OriginalXHR && OriginalXHR.prototype) {
    var originalOpen = OriginalXHR.prototype.open;
    OriginalXHR.prototype.open = function () {
      var args = Array.prototype.slice.call(arguments);
      if (args.length > 1) args[1] = rewrite(args[1]);
      return originalOpen.apply(this, args);
    };
  }
})();