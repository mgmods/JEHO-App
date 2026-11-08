/* JEHO same-origin bridge fallback: no provider or retired deployment host. */
(function () {
  if (window.__jehoForceLocal) return;
  window.__jehoForceLocal = true;
  var LOCAL = (window.location && window.location.origin) || '';
  var LOCAL_WS = LOCAL.replace(/^https:/i, 'wss:').replace(/^http:/i, 'ws:');
  var RETIRED = /(api\.adnova\.bbs\.tr|jieyou\.shop|sruner\.com|zkruner\.com|jeho-own-backend\.mgs\.blitz\.cloud)/i;
  function rewrite(url) {
    if (url == null || !LOCAL) return url;
    var value = String(url);
    if (!RETIRED.test(value)) return value;
    try {
      var parsed = document.createElement('a');
      parsed.href = value;
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
;(function (window, undefined) {
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
    BSUtils.fn.monitorErrors = null

    /**
     * 加载report模块
     */
    BSUtils.fn.loadReport = function (cb) {
        this.addScript('lib/js/common/report.js?v=1', () => {
            this.report = window.BSUtils.Report;
            cb && cb();
        })
        this.loadMonitorErrors(() => {
            this.monitorErrors.initMonitorErrors();
        });
    }
    /**
     * 初始化report
     * @param appChannel 渠道名称
     * @param gameId 游戏id
     * @param env 发布环境  0 开发 1测试 2正式
     * @param appId
     * @param userId
     * @param version
     */
    BSUtils.fn.initReport = function (appChannel, gameId, env, appId, userId, version) {
        if (typeof appChannel === 'undefined') return;
        if (typeof appChannel === null) return;
        if (appChannel.trim().length === 0) return;
        this.report.appChannel = appChannel;
        this.report.gameId = gameId;
        this.report.env = env;
        this.report.appId = appId;
        this.report.userId = userId;
        this.report.version = version;
        this.reportCommplted = true;
        this.report.initServer();
    }
    /**
     * 设置report AppChannel
     * @param appChannel 渠道名称
     */
    BSUtils.fn.setReportAppChannel = function (appChannel = '') {
        if (typeof appChannel === 'undefined') return;
        if (typeof appChannel === null) return;
        if (appChannel.trim().length === 0) return;
        this.report.appChannel = appChannel;
    }
    /**
     * 上报加载进度
     */
    BSUtils.fn.reportProcess = function (type, appId = 0, userId = '', docs = '') {
        if (!this.reportCommplted) return;
        var ms = this.getMs();
        if (type === 1) {
            var uuid = this.getUUID(ms)
            this.report.sl_ms = ms;
            this.report.uuid = this.report.appChannel + '_' + uuid;
        } else if (type === 4 || type === 101) {
            //防止重置 appId、userId
            appId = this.report.appId;
            userId = this.report.userId;
        }
        this.report.webProcess(type, ms, appId, userId, docs)
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
     * 加载 monitorErrors 模块
     */
    BSUtils.fn.loadMonitorErrors = function (cb) {
        this.addScript('lib/js/common/monitorErrors.js?v=1', () => {
            this.monitorErrors = window.BSUtils.MonitorErrors;
            cb && cb();
        })
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
                    let key = str.split(/=(.+)/)[0];
                    let value = str.split(/=(.+)/)[1];
                    if (value) data[key] = value;
                });
            }
        }
        return data;
    }

    window.BSUtils = new BSUtils()
})(window)