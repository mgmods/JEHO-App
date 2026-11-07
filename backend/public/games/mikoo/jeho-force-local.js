/* JEHO: keep every packaged Mikoo game on the single origin serving it. */
(function () {
  if (window.__jehoForceLocal) return;
  window.__jehoForceLocal = true;

  var LOCAL = (window.location && window.location.origin) || '';
  var LOCAL_WS = LOCAL.replace(/^https:/i, 'wss:').replace(/^http:/i, 'ws:');
  var RETIRED = /(api\.adnova\.bbs\.tr|jieyou\.shop|sruner\.com|zkruner\.com|jeho-own-backend\.mgs\.blitz\.cloud)/i;

  // DOMAIN/domain was a provider-side override and could point one game at an
  // unrelated host. Remove it before game bundles read location.search.
  try {
    var pageUrl = new URL(window.location.href);
    pageUrl.searchParams.delete('DOMAIN');
    pageUrl.searchParams.delete('domain');
    if (window.history && window.history.replaceState &&
        pageUrl.href !== window.location.href) {
      window.history.replaceState(window.history.state, '', pageUrl.href);
    }
  } catch (ignored) {}

  // Expose only this deployment origin to legacy bundles that read window.DOMAIN.
  try {
    ['DOMAIN', 'domain'].forEach(function (key) {
      Object.defineProperty(window, key, {
        configurable: true,
        enumerable: true,
        get: function () { return LOCAL; },
        set: function () { /* ignore provider overrides */ }
      });
    });
  } catch (ignored) {
    window.DOMAIN = LOCAL;
    window.domain = LOCAL;
  }

  function rewrite(url) {
    if (url == null || !LOCAL) return url;
    var value = String(url);
    // Rewrite retired provider hosts and any external host supplied through a
    // legacy domain override. Keep relative and same-origin package assets intact.
    if (!RETIRED.test(value)) return value;
    try {
      var parsed = new URL(value, LOCAL);
      var path = (parsed.pathname || '/') + (parsed.search || '') + (parsed.hash || '');
      return /^wss?:/i.test(value) ? LOCAL_WS + path : LOCAL + path;
    } catch (e) {
      return value.replace(/^https?:\/\/[^/]+/i, LOCAL)
        .replace(/^wss?:\/\/[^/]+/i, LOCAL_WS);
    }
  }

  var OriginalWebSocket = window.WebSocket;
  if (OriginalWebSocket) {
    window.WebSocket = function (url, protocols) {
      var target = rewrite(url);
      return protocols !== undefined
        ? new OriginalWebSocket(target, protocols)
        : new OriginalWebSocket(target);
    };
    window.WebSocket.prototype = OriginalWebSocket.prototype;
    ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach(function (key) {
      window.WebSocket[key] = OriginalWebSocket[key];
    });
  }

  if (window.fetch) {
    var originalFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      if (typeof input === 'string' || (typeof URL !== 'undefined' && input instanceof URL)) {
        input = rewrite(input);
      } else if (input && typeof Request !== 'undefined' && input instanceof Request) {
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

  if (window.EventSource) {
    var OriginalEventSource = window.EventSource;
    window.EventSource = function (url, config) {
      return new OriginalEventSource(rewrite(url), config);
    };
    window.EventSource.prototype = OriginalEventSource.prototype;
  }

  if (navigator.sendBeacon) {
    var originalSendBeacon = navigator.sendBeacon.bind(navigator);
    navigator.sendBeacon = function (url, data) {
      return originalSendBeacon(rewrite(url), data);
    };
  }
})();
