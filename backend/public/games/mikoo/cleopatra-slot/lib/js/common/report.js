;(function (window, undefined) {
    function Report() {
        if (!(this instanceof Report))
            return new Report()
    }

    //appChannel
    Report.prototype.appChannel = ''
    //gameId
    Report.prototype.gameId = 0
    //env
    Report.prototype.env = 0
    //appId
    Report.prototype.appId = 0
    //userId
    Report.prototype.userId = ''
    //version
    Report.prototype.version = ''

    //生成的模拟用户id
    Report.prototype.uuid = ''
    //uuid中的时间戳
    Report.prototype.sl_ms = 0

    Report.prototype.httpAddressList = []
    Report.prototype.reportTimeout = 5000
    Report.prototype.currentHttpIndex = 0
    Report.prototype.reportQueue = [];
    Report.prototype.isSending = false;
    Report.prototype.maxQueueSize = 100;

    Report.prototype.initServer = function () {
        var path = '';

        if (this.env === 0) {
            path = '/client_log/dev/add';
        } else if (this.env === 1) {
            path = '/client_log/test/add';
        } else {
            path = '/client_log/prod/add';
        }
        // JEHO only — never call BaiShun/jieyou/sruner/zkruner.
        this.httpAddressList = [
            'https://api.adnova.bbs.tr/games/route' + path
        ];
        this.currentHttpIndex = 0;
        // 单次请求超时时间
        this.reportTimeout = 5000;
    }

    Report.prototype._createReportId = function () {
        return [
            this.uuid,
            Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
        ].join("_");
    };
    /**
     * 上报加载进度
     * @param type 类型
     * 1:成功拉起游戏
     * 2:Loading开始加载
     * 3:Loading加载完成
     * 4.getConfig数据请求
     * 5.getConfig数据回包
     * 6:游戏登录成功，进入游戏主页
     * 7:不支持WebGL
     * 8:发起获取路由请求
     * 9:获取成功路由请求
     * 10:发起连接服务器
     * 11:连接服务成功
     * 12:收到Connect消息
     * 901:连接服务失败
     * 101:js相关错误上报
     * 102:自定义数据上报
     * @param ms 时间戳
     * @param appId 商户Id
     * @param userId 用户真实Id
     */
    Report.prototype.webProcess = function (type, ms, appId, userId, docs) {
        this.appId = appId;
        this.userId = userId;

        var data = {
            uuid: this.uuid,
            sl_ms: this.sl_ms,
            user_id: userId,
            app_id: parseInt(appId),
            app_channel: this.appChannel,
            game_id: this.gameId,
            ms_time: ms,
            type: type,
            env: this.env,
            version: this.version,
            docs: docs,
            report_id: this._createReportId()
        }
        this._enqueueReport(data);
    }

    Report.prototype._enqueueReport = function (data) {
        if (this.reportQueue.length >= this.maxQueueSize) {
            this.reportQueue.shift();
        }
        this.reportQueue.push(data);
        this._flushReportQueue();
    };

    Report.prototype._flushReportQueue = function () {
        var self = this;
        if (this.isSending) {
            return;
        }
        var data = this.reportQueue.shift();
        if (!data) {
            return;
        }
        this.isSending = true;
        this._sendReportWithFallback(data, this.currentHttpIndex, function () {
            self.isSending = false;
            self._flushReportQueue();
        });
    };

    Report.prototype._sendReportWithFallback = function (data, startIndex, callBack) {
        var self = this;
        var list = this.httpAddressList;
        if (!list || list.length === 0) {
            callBack && callBack(false);
            return;
        }
        var len = list.length;
        var tryCount = 0;
        var index = typeof startIndex === "number" ? startIndex : this.currentHttpIndex;

        function tryNext() {
            if (tryCount >= len) {
                callBack && callBack(false);
                return;
            }
            var realIndex = index % len;
            var url = list[realIndex];
            tryCount++;
            self._postJson(url, data, function (success) {
                if (success) {
                    self.currentHttpIndex = realIndex;
                    callBack && callBack(true);
                    return;
                }
                index++;
                tryNext();
            });
        }

        tryNext();
    };

    Report.prototype._postJson = function (url, data, callback) {
        var xhr = new XMLHttpRequest();
        var finished = false;

        function done(success) {
            if (finished) return;
            finished = true;
            callback(success);
        }

        try {
            xhr.open('POST', url, true);
            xhr.timeout = this.reportTimeout || 5000;
            xhr.setRequestHeader('Content-Type', 'application/json');
            xhr.onreadystatechange = function () {
                if (xhr.readyState !== 4 || finished) {
                    return;
                }
                // 2xx 都认为成功，不只判断 200
                if (xhr.status >= 200 && xhr.status < 300) {
                    done(true);
                } else {
                    done(false);
                }
            };
            xhr.onerror = function () {
                done(false);
            };

            xhr.ontimeout = function () {
                done(false);
            };

            xhr.send(JSON.stringify(data));
        } catch (e) {
            done(false)
        }
    };

    window.BSUtils.Report = new Report()
})(window)