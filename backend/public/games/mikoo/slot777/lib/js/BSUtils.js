/* JEHO force-local: rewrite jieyou/sruner/zkruner → api.adnova.bbs.tr */
(function(){if(window.__jehoForceLocal)return;window.__jehoForceLocal=1;var LOCAL='https://api.adnova.bbs.tr',LOCAL_WS='wss://api.adnova.bbs.tr',BAD=/(jieyou\.shop|sruner\.com|zkruner\.com)/i;function rewrite(u){if(u==null)return u;var s=String(u);if(!BAD.test(s))return s;try{var a=document.createElement('a');a.href=s;var path=(a.pathname||'/')+(a.search||'')+(a.hash||'');return/^wss?:/i.test(s)?(LOCAL_WS+path):(LOCAL+path)}catch(e){return s.replace(/^https?:\/\/[^/]+/i,LOCAL).replace(/^wss?:\/\/[^/]+/i,LOCAL_WS)}}try{var q=new URLSearchParams(location.search||'');var dom=q.get('DOMAIN')||q.get('domain');if(dom&&!BAD.test(dom)){LOCAL=String(dom).replace(/\/$/,'');if(/^https?:/i.test(LOCAL))LOCAL_WS=LOCAL.replace(/^https:/i,'wss:').replace(/^http:/i,'ws:')}}catch(e){}var OW=window.WebSocket;if(OW){window.WebSocket=function(u,p){u=rewrite(u);return p!==undefined?new OW(u,p):new OW(u)};window.WebSocket.prototype=OW.prototype;window.WebSocket.CONNECTING=OW.CONNECTING;window.WebSocket.OPEN=OW.OPEN;window.WebSocket.CLOSING=OW.CLOSING;window.WebSocket.CLOSED=OW.CLOSED}if(window.fetch){var of=window.fetch.bind(window);window.fetch=function(i,n){if(typeof i==='string')i=rewrite(i);else if(i&&typeof Request!=='undefined'&&i instanceof Request)i=new Request(rewrite(i.url),i);return of(i,n)}}var XO=window.XMLHttpRequest;if(XO&&XO.prototype){var op=XO.prototype.open;XO.prototype.open=function(){var a=Array.prototype.slice.call(arguments);if(a.length>1)a[1]=rewrite(a[1]);return op.apply(this,a)}}})();
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
        // JEHO: rewrite external BaiShun hosts before any network I/O.
        this.addScript('lib/js/common/jeho-force-local.js?v=1', () => {
            this.addScript('lib/js/common/report.js?v=1', () => {
                this.report = window.BSUtils.Report;
                cb && cb();
            })
        });
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