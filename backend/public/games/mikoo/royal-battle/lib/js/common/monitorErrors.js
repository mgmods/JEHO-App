;(function (window, undefined) {
    function MonitorErrors() {
        if (!(this instanceof MonitorErrors))
            return new MonitorErrors()

        this._xhrHooked = false
        this._windowErrorHooked = false
        this._promiseErrorHooked = false
        this._imageHooked = false
    }

    MonitorErrors.prototype.initMonitorErrors = function () {
        if (!this._windowErrorHooked) {
            this.addWindowError()
            this._windowErrorHooked = true
        }

        if (!this._promiseErrorHooked) {
            this.addPromiseError()
            this._promiseErrorHooked = true
        }

        if (!this._xhrHooked) {
            this.overrideXHR()
            this._xhrHooked = true
        }

        if (!this._imageHooked) {
            this.overrideImage();
            this._imageHooked = true;
        }
    }

    MonitorErrors.prototype.addWindowError = function () {
        var self = this;
        window.addEventListener("error", function (err) {
            var errMsg = "";
            var target = err.target || err.srcElement
            if (target && target !== window && (target.src || target.href)) {
                errMsg = "resource load error: " + (target.src || target.href)
            } else if (typeof ErrorEvent !== "undefined" && err instanceof ErrorEvent) {
                errMsg = String(err.message) +
                    ", filename: " + String(err.filename) +
                    ", lineno: " + String(err.lineno) +
                    ", colno: " + String(err.colno)

                if (err.error && err.error.stack) {
                    errMsg += ", stack: " + err.error.stack
                }
            } else {
                errMsg = "unknown window error"
            }
            self.reportError_bs("[BS Window Error] " + errMsg);
        }, true);
    }

    MonitorErrors.prototype.addPromiseError = function () {
        var self = this
        window.addEventListener("unhandledrejection", function (event) {
            var reason = event.reason
            var msg = ""
            if (reason instanceof Error) {
                msg = reason.stack || reason.message
            } else {
                try {
                    msg = JSON.stringify(reason)
                } catch (e) {
                    msg = String(reason)
                }
            }
            self.reportError_bs("[BS Promise Error] " + msg)
        })
    }

    MonitorErrors.prototype.overrideXHR = function () {
        if (!window.XMLHttpRequest) return;
        var originOpen = XMLHttpRequest.prototype.open;
        var originSend = XMLHttpRequest.prototype.send;
        var originSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
        var self = this;

        function shouldIgnoreXHR(url) {
            if (!url) return false
            return (
                url.indexOf('/client_log/dev/add') !== -1 ||
                url.indexOf('/client_log/test/add') !== -1 ||
                url.indexOf('/client_log/prod/add') !== -1
            )
        }

        function getAbsoluteUrl(url) {
            if (!url || typeof url !== "string") {
                return "";
            }
            try {
                return new URL(url, window.location.href).href;
            } catch (e) {
                return url;
            }
        }

        function hasResponseContent(xhr) {
            try {
                if (typeof xhr.responseText === "string" && xhr.responseText.length > 0) {
                    return true;
                }
            } catch (e) {
            }

            try {
                if (xhr.response) {
                    return true;
                }
            } catch (e) {
            }
            return false;
        }

        function isProbablyLocalOrRelative(rawUrl) {
            if (!rawUrl || typeof rawUrl !== "string") {
                return false;
            }
            if (/^(file:|data:|blob:|javascript:|about:|android_asset:|app:|capacitor:|ionic:)/i.test(rawUrl)) {
                return true;
            }
            if (!/^https?:\/\//i.test(rawUrl)) {
                return true;
            }
            return false;
        }

        function safeStringify(obj) {
            try {
                return JSON.stringify(obj)
            } catch (e) {
                return String(obj)
            }
        }

        function reportXHR(type, info) {
            var errorLog = safeStringify({
                type: type,
                info: info
            })
            self.reportError_bs('[BS XHR Monitor] ' + errorLog);
        }

        XMLHttpRequest.prototype.open = function (method, url, async, user, password) {
            this.__xhr_monitor = {
                method: method,
                url: typeof url === "string" ? url : String(url),
                async: async !== false,
                startTime: 0,
                requestHeaders: {}
            };
            return originOpen.apply(this, arguments);
        };

        XMLHttpRequest.prototype.setRequestHeader = function (name, value) {
            if (this.__xhr_monitor) {
                this.__xhr_monitor.requestHeaders[name] = value;
            }
            return originSetRequestHeader.apply(this, arguments);
        };

        XMLHttpRequest.prototype.send = function (body) {
            var xhr = this;
            var monitor = xhr.__xhr_monitor || {};
            var finished = false;
            monitor.startTime = Date.now();

            function finish(type, reason, extra) {
                if (finished) return;
                finished = true;

                var rawUrl = monitor.url || "";
                var url = getAbsoluteUrl(rawUrl);

                if (shouldIgnoreXHR(url)) {
                    return
                }

                var info = {
                    reason: reason,
                    method: monitor.method,
                    url: url,
                    status: xhr.status,
                    rawUrl: rawUrl,
                    readyState: xhr.readyState,
                    costTime: Date.now() - monitor.startTime
                };
                if (extra) {
                    for (var key in extra) {
                        info[key] = extra[key];
                    }
                }
                reportXHR(type, info);
            }

            /**
             * loadend：无论成功、失败、超时,最后基本都会触发
             * 这里统一兜底判断 status
             */
            xhr.addEventListener("loadend", function () {
                // status === 0：网络失败 / CORS / DNS / TLS / 连接关闭 / 被拦截 等
                if (xhr.status === 0) {
                    // 本地资源，status=0 但有内容，认为不是失败
                    if (isProbablyLocalOrRelative(monitor.url || "") && hasResponseContent(xhr)) {
                        return;
                    }

                    finish("xhr_network_error", "status_0");
                    return;
                }

                // HTTP 状态码异常
                if (xhr.status >= 400) {
                    finish("xhr_http_error", "bad_status", {
                        responseText: safeResponseText(xhr)
                    });
                }
            });

            xhr.addEventListener("error", function (event) {
                finish("xhr_error", "network_error", {
                    eventType: event && event.type
                });
            });

            xhr.addEventListener("timeout", function () {
                finish("xhr_timeout", "timeout");
            });

            xhr.addEventListener("abort", function () {
                finish("xhr_abort", "abort")
            })

            try {
                return originSend.apply(xhr, arguments);
            } catch (e) {
                finish("xhr_send_exception", "send_exception", {
                    errorName: e && e.name,
                    errorMessage: e && e.message ? e.message : String(e)
                });

                throw e;
            }
        };

        function safeResponseText(xhr) {
            try {
                return xhr.responseText;
            } catch (e) {
                return "";
            }
        }
    }

    MonitorErrors.prototype.overrideImage = function () {
        var self = this;
        if (!window.Image) {
            return;
        }
        var OriginImage = window.Image;
        window.Image = function (width, height) {
            var img;
            if (arguments.length === 0) {
                img = new OriginImage();
            } else if (arguments.length === 1) {
                img = new OriginImage(width);
            } else {
                img = new OriginImage(width, height);
            }

            try {
                img.addEventListener("error", function () {
                    var url = img.currentSrc || img.src || "";
                    self.reportError_bs("[BS Image Error] " + JSON.stringify({
                        url: url
                    }));
                });
            } catch (e) {
            }

            return img;
        };

        try {
            window.Image.prototype = OriginImage.prototype;
        } catch (e) {
        }
    }

    MonitorErrors.prototype.reportError_bs = function (msg) {
        try {
            var bsUtils = window.BSUtils
            if (bsUtils && typeof bsUtils.reportProcess === 'function') {
                bsUtils.reportProcess(101, 0, '', msg)
            }
        } catch (e) {
        }

    }

    window.BSUtils = window.BSUtils || {}
    window.BSUtils.MonitorErrors = new MonitorErrors()
})(window)