/* JEHO: never talk to BaiShun/jieyou — rewrite to local API. */
(function () {
  if (window.__jehoForceLocal) return;
  window.__jehoForceLocal = 1;
  var LOCAL = 'https://api.adnova.bbs.tr';
  var LOCAL_WS = 'wss://api.adnova.bbs.tr';
  var BAD = /(jieyou\.shop|sruner\.com|zkruner\.com|soofun\.online)/i;

  function fixPath(path) {
    // BaiShun historical path is /game_route; JEHO serves /games/route.
    if (!path) return path;
    return String(path)
      .replace(/\/game_route(?=\/|\?|$)/gi, '/games/route')
      .replace(/\/game-route(?=\/|\?|$)/gi, '/games/route');
  }

  function rewrite(u) {
    if (u == null) return u;
    var s = String(u);
    var needsHost = BAD.test(s);
    var needsPath = /\/game_route(\/|\?|$)/i.test(s) || /\/game-route(\/|\?|$)/i.test(s);
    if (!needsHost && !needsPath) return s;
    try {
      var a = document.createElement('a');
      a.href = s;
      var path = fixPath((a.pathname || '/') + (a.search || '') + (a.hash || ''));
      if (/^wss?:/i.test(s)) {
        var wsOrigin = needsHost ? LOCAL_WS : a.protocol + '//' + a.host;
        return wsOrigin.replace(/\/$/, '') + path;
      }
      var origin = needsHost ? LOCAL : a.protocol + '//' + a.host;
      return origin.replace(/\/$/, '') + path;
    } catch (e) {
      var out = s;
      if (needsHost) {
        out = out
          .replace(/^https?:\/\/[^/]+/i, LOCAL)
          .replace(/^wss?:\/\/[^/]+/i, LOCAL_WS);
      }
      return fixPath(out);
    }
  }

  try {
    var q = new URLSearchParams(location.search || '');
    var dom = q.get('DOMAIN') || q.get('domain');
    if (dom && !BAD.test(dom)) {
      window.__JEHO_DOMAIN = dom;
      LOCAL = String(dom).replace(/\/$/, '');
      // Keep host only — never keep /games/route path on LOCAL_WS.
      try {
        var u = new URL(LOCAL.indexOf('http') === 0 ? LOCAL : 'https://' + LOCAL);
        LOCAL = u.origin;
      } catch (e2) {}
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

;;(function (window, undefined) {
    function BSUtils() {
        if (!(this instanceof BSUtils))
            return new BSUtils()
    }

    BSUtils.fn = BSUtils.prototype = {
        BSUtils: '1.0.0'
    }
    BSUtils.fn.report = null
    BSUtils.fn.reportCommplted = false

    BSUtils.fn.appChannel = null

    /**
     * 加载report模块
     */
    BSUtils.fn.loadReport = function (cb) {
        this.addScript('lib/js/common/report.js?v=1', () => {
            this.report = window.BSUtils.Report;
            cb && cb();
        })
    }
    /**
     * 初始化report
     * @param appChannel 渠道名称
     * @param gameId 游戏id
     * @param env 发布环境  0 开发 1测试 2正式
     */
    BSUtils.fn.initReport = function (appChannel, gameId, env) {
        if (typeof appChannel === 'undefined') return;
        if (typeof appChannel === null) return;
        if (appChannel.trim().length === 0) return;
        this.report.appChannel = appChannel;
        this.report.gameId = gameId;
        this.report.env = env;
        this.reportCommplted = true;
        this.report.initServer();
    }
    /**
     * 上报加载进度
     */
    BSUtils.fn.reportProcess = function (type, appId = 0, userId = '') {
        if (!this.reportCommplted) return;
        var ms = this.getMs();
        if (type === 1) {
            var uuid = this.getUUID(ms)
            this.report.sl_ms = ms;
            this.report.uuid = this.report.appChannel + '_' + uuid;
        }
        this.report.webProcess(type, ms, appId, userId)
    }

    /**
     * 加载appChannel模块
     */
    BSUtils.fn.loadAppChannel = function (cb) {
        this.addScript('lib/js/common/appChannel.js?v=1', () => {
            this.appChannel = window.BSUtils.AppChanel;
            cb && cb();
        })
    }
    /**
     * 初始化channel
     * @param appChannel
     */
    BSUtils.fn.initAppChannel = function (appChannel) {
        if (typeof appChannel === 'undefined') return;
        if (typeof appChannel === null) return;
        if (appChannel.trim().length === 0) return;
        this.appChannel.channel = appChannel;
        this.appChannel.init();
    }

    /**
     *
     * @param moduleName
     * @param cb
     */
    BSUtils.fn.addScript = function (moduleName, cb) {
        function scriptLoaded() {
            document.body.removeChild(domScript);
            domScript.removeEventListener('load', scriptLoaded, false);
            cb && cb();
        }

        var domScript = document.createElement('script');
        domScript.async = true;
        domScript.src = moduleName;
        domScript.addEventListener('load', scriptLoaded, false);
        document.body.appendChild(domScript);
    }
    /**
     * 生成时间戳
     * @returns {number}
     */
    BSUtils.fn.getMs = function () {
        return Date.now();
    }
    /**
     * 生成一个永不重复的ID
     * 36进制字符串
     * @param ms 时间戳
     * @param randomLength 长度
     * @returns {string}
     */
    BSUtils.fn.getUUID = function (ms, randomLength = 10) {
        return Number(Math.random().toString().substr(2, randomLength) + ms).toString(36);
    }
    /**
     * 获取URL拼接参数
     * @param url
     * @returns {{}}
     * @constructor
     */
    BSUtils.fn.GetUrlParams = function (url) {
        let urlArr = url.split("?");
        let data = {};
        if (urlArr.length === 1) return data;
        for (let i = 1; i <= urlArr.length - 1; i++) {
            let paramsStr = decodeURIComponent(urlArr[i]);
            if (paramsStr && paramsStr !== 'undefined') {
                let paramsArr = paramsStr.split("&");
                paramsArr.forEach((str) => {
                    let key = str.split("=")[0];
                    let value = str.split("=")[1];
                    if (value) data[key] = value;
                });
            }
        }
        return data;
    }

    window.BSUtils = new BSUtils()
})(window)