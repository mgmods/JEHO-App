(function (root, factory) {
    typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports) :
        typeof define === 'function' && define.amd ? define(['exports'], factory) :
            (global = typeof globalThis !== 'undefined' ? globalThis : window.global = window || self, factory(global.ImoSDK = {}));
}(this,
    function (exports) {
        'use strict';
        var Protocal =  (function () {
            function Protocal(methodName, id, params, onComplete, onReceive, timeout) {
                var _this = this;
                var key = params['callName'] + "_" + id;
                this._receiveName = key + "_receive";
                this._completeName = key + "_complete";
                params = params || {};
                // params['receive'] = this._receiveName;
                // params['jsCallback'] = this._completeName;
                params['callBackId'] = this._completeName;
                if(params.callName && params.callName == 'getConfig'){
                    ImoSDK.prototype.getConfigCallBackId = this._completeName;
                }
                console.log('[MeshH5]', 'params', params);
                window[this._receiveName] = function (result) {
                    console.log('[MeshH5]', 'native:', "methodName " + key + " received");
                    window[_this._receiveName] = null;
                    _this.done();
                    if (onReceive) {
                        onReceive(null, result);
                    }
                };
                var flag = false;
                window[this._completeName] = function (result) {
                    console.log('[MeshH5]', 'native:', "methodName " + key + " completed", result);
                    flag = true;
                    _this.dispose();
                    if (onComplete) {
                        onComplete(null, result);
                    }
                };
                if(Utils.isIframe()){
                    if(methodName == 'sendGameAction'){
                        if(params.type == 1 ){
                            flag = true;
                            _this.dispose();
                            return;
                        }
                    }
                    window.addEventListener('message',e=>{
                        if(e.data && window[this._completeName]){
                            var data = e.data;
                            var jsMethods = data.jsMethods;
                            if(!jsMethods) return;
                            var jsData = data.jsData;
                            if(!jsData) return;
                            var result = ImoSDK.prototype.parseResult(undefined, jsData);
                            if (!result)
                                return;
                            console.log('[MeshH5]', 'iFrame:', "methodName " + jsMethods + " completed", result);
                            flag = true;
                            _this.dispose();
                            if (onComplete) {
                                onComplete(null, result);
                            }
                        }
                    },false);
                }
                if (timeout > 0) {
                    this._completeTimeoutId = setTimeout(function () {
                        clearTimeout(_this._completeTimeoutId);
                        if (onComplete && !flag) {
                            _this.dispose();
                            ImoSDK.prototype.webProcess({"process":'6'});
                            ImoSDK.prototype.destroy();
                            onComplete("methodName " + key + " not responding", null);
                        }
                    }, timeout);
                }
            }
            Protocal.prototype.onDone = function (callback) {
                if (this._receiveName && this._completeName)
                    this._onDone = callback;
            };
            Protocal.prototype.done = function () {
                if (this._onDone) {
                    this._onDone();
                    this._onDone = undefined;
                }
            };
            Protocal.prototype.dispose = function () {
                this.done();
                window[this._receiveName] = null;
                delete window[this._receiveName];
                this._receiveName = null;
                window[this._completeName] = null;
                delete window[this._completeName];
                this._completeName = null;
                clearTimeout(this._completeTimeoutId);
            };
            Protocal.TIME_OUT_IN_MS = 1500;
            return Protocal;
        }());
        var Native =  (function () {
            function Native() {
            }
            Native.invoke = function (methodName,protocal, params, onComplete, onReceive, timeout
            ) {
                var _this = this;
                if (timeout === void 0) { timeout = 5000; }
                var proto = this.createProtocal(methodName, params, onComplete, onReceive, timeout);
                if (proto) {
                    var isIframe = Utils.isIframe();
                    if(Utils.isAndroid()){
                        if(isIframe){
                            console.log('android iframe');
                            //向iframe发送消息
                            window.parent.postMessage(JSON.stringify(params),"*");
                        }else {
                            if(methodName == 'sendGameAction'){
                                if(protocal){
                                    protocal.call(window.gameBridge,JSON.stringify(params));
                                }
                            }else{
                                protocal.call(window.gameBridge,JSON.stringify(params));
                            }
                        }
                    }else if(Utils.isIOS()){
                        if(isIframe){
                            console.log('ios iframe');
                            //向iframe发送消息
                            window.parent.postMessage(JSON.stringify(params),"*");
                        }else {
                            if(methodName == 'sendGameAction'){
                                if(protocal){
                                    protocal.postMessage(JSON.stringify(params));
                                }
                            }else{
                                protocal.postMessage(JSON.stringify(params));
                            }
                        }
                    }else {
                        if(isIframe){
                            console.log('pc iframe');
                            //向iframe发送消息
                            window.parent.postMessage(JSON.stringify(params),"*");
                        }else {
                            console.error('[MeshH5]', 'native:', 'invalid Android&Ios');
                        }
                    }
                }
                else {
                    console.error('[MeshH5]', 'native:', 'invalid protocal initliazation');
                }
            };
            Native.createProtocal = function (methodName, params, onComplete, onReceive, timeout) {
                var defaultTotal = localStorage.getItem('requestTotal') || 0;
                if(parseInt(defaultTotal)>10000){
                    localStorage.setItem('requestTotal',0);
                    defaultTotal = 0;
                }
                var total = this.PROTOCAL_CACHE[methodName] || parseInt(defaultTotal);
                total += 1;
                localStorage.setItem('requestTotal',total);
                this.PROTOCAL_CACHE[methodName] = total;
                return new Protocal(methodName, total, params, onComplete, onReceive, timeout);
            };
            Native._allIFrames = [];
            Native._iframeId = -1;
            Native.PROTOCAL_CACHE = {};
            return Native;
        }());
        var isAndroid = function (){
            var u = navigator.userAgent;
            return u.indexOf('Android') > -1 || u.indexOf('Adr') > -1;
        };
        var isIOS = function (){
            var u = navigator.userAgent;
            return !!u.match(/\(i[^;]+;( U;)? CPU.+Mac OS X/);
        };
        var isIframe = function (){
            return window.top !== window;
        };
        var nativeCall = Native.invoke.bind(Native);
        var Utils = Object.freeze({
            nativeCall: nativeCall,
            isAndroid:isAndroid,
            isIOS:isIOS,
            isIframe:isIframe,
        });
        var ImoSDK = (function () {
            function ImoSDK() {
                /*注册H5接受消息*/
                this._wrappedConfigParams = {
                    /**
                     *  用户货币发生变化
                     *  @param result
                     *  {
                     *    "balance" : 100,
                     *    "openId" : "1234"
                     *  }
                     */
                    'walletUpdate':function (handler,result){
                        var cb = handler,data = result;
                        if(cb){
                            cb(data);
                        }
                    },
                    /**
                     * 观众人数变化
                     * @param result
                     * {
                     *   "audienceCount" : 100, //最新人数
                     * }
                     */
                    'audienceUpdate':function (handler,result){
                        var cb = handler,data = result;
                        if(cb){
                            cb(data);
                        }
                    },
                }
            }
            ImoSDK.prototype.getConfigCallBackId = '';
            /**
             * 创建协议
             * @param fnName 方法名
             */
            ImoSDK.prototype.createProtocol = function (fnName) {
                var isIframe = Utils.isIframe();
                if(Utils.isAndroid()){
                    if(isIframe){
                        console.log('android iframe');
                    }else {
                        return window.gameBridge[fnName];
                    }
                }else if(Utils.isIOS()){
                    if(isIframe){
                        console.log('ios iframe');
                    }else {
                        return window.webkit.messageHandlers[fnName];
                    }
                }else {
                    if(isIframe){
                        console.log('pc iframe');
                    }else {
                        console.error('[Mesh-H5-sdk]', "createProtocol 既不是Andoroid也不是Ios");
                    }
                }
            };
            /**
             * 通用的处理结果的方法
             * @param err 错误信息
             * @param result 成功的结果，一般是字符串，需要JSON.parse处理
             * @returns {*}
             */
            ImoSDK.prototype.parseResult = function (err,result){
                if (err)
                    throw err;
                else if (result == undefined || result == null)
                    throw 'empty result';
                else {
                    var parsed = void 0;
                    if (typeof result == 'string') {
                        parsed = JSON.parse(result);
                    }
                    else if (typeof result == 'object') {
                        parsed = result;
                    }
                    if (!parsed)
                        throw 'failed to parse';
                    return parsed;
                }
            };
            /**
             * 辅助方法，用来方便创建统一的协议回调处理逻辑
             * @param resolve 通过 promise 传入的 resolve 回调
             * @param reject 通过 promise 传入的 reject 回调
             */
            ImoSDK.prototype.usePromiseResult = function (resolve, reject) {
                var _this = this;
                return function (err, result) {
                    try {
                        var parsed = _this.parseResult(err, result);
                        if (parsed) {
                            resolve(parsed);
                        }
                    }
                    catch (e) {
                        reject(e);
                    }
                };
            };
            /**
             * 发送协议的接口。
             * 一般情况下无需主动调用
             * @param params 构造出来的协议参数，包括协议名和参数等
             * @param onComplete 完成回调
             * @param onReceive 接收到回调
             */
            ImoSDK.prototype.send = function (params, onComplete, onReceive) {
                if (!params)
                    return;
                Utils.nativeCall(params.methodName,params.protocol, params.params, onComplete, onReceive, params.timeout);
            };
            /**
             * 通用的请求协议的方法。可以参照协议文档进行调用。
             * @param methodName 方法函数名
             * @param params 协议参数，JSON Object
             * @param onComplete 协议完成回调方法
             * @param onReceived 协议收到回调方法（ACK）
             * @param timeout 超时，单位毫秒。默认 0 为不超时。超时设置的用处是清空回调等资源。
             */
            ImoSDK.prototype.request = function (methodName, params, onComplete, onReceived, timeout) {
                if (timeout === void 0) { timeout = 0; }
                this.send({
                    methodName:methodName,
                    protocol: this.createProtocol(methodName),
                    params: params,
                    timeout: timeout,
                }, this.usePromiseResult(onComplete, function (reason) { return console.error('[Mesh-H5-sdk]', "methodName " + methodName + " error: " + reason); }), onReceived);
            };
            ImoSDK.prototype.regReceiveMessage = function (params) {
                var _this = this;
                var wrapped = {};
                for (var key in params) {
                    if (!!_this._wrappedConfigParams[key]) {
                        wrapped[key] = key;
                    }
                }
                if(Utils.isIframe()){
                    window.addEventListener('message',e=>{
                        if(e.data){
                            var data = e.data;
                            var jsMethods = data.jsMethods;
                            if (!params[jsMethods])
                                return;
                            var jsData = data.jsData;
                            if(!jsData) return;
                            var result = ImoSDK.prototype.parseResult(undefined, jsData);
                            if (!result)
                                return;
                            if (typeof params[jsMethods] === 'function' || !!_this._wrappedConfigParams[jsMethods]) {
                                _this._wrappedConfigParams[jsMethods](params[jsMethods], result);
                            }
                        }
                    });
                }else {
                    // 把包裹后的方法注册到 window 上
                    Object.keys(wrapped).forEach(function (key) {
                        var name = wrapped[key];
                        if (name) {
                            window[name] = function (str) {
                                var result = _this.parseResult(undefined, str);
                                if (!result)
                                    return;
                                if (!params[key])
                                    return;
                                if (typeof params[key] === 'function' || !!_this._wrappedConfigParams[key]) {
                                    _this._wrappedConfigParams[key](params[key], result);
                                }
                            };
                        }
                    });
                    window['sendMessageByClient'] = function (result){
                        var currentResult = null;
                        if (typeof result == 'string') {
                            currentResult = JSON.parse(result);
                        }
                        else if (typeof result == 'object') {
                            currentResult = result;
                        }
                        console.log('[MeshH5]', 'ImoNative:', currentResult);
                        if(currentResult.code == 0){
                            if(currentResult.callBackId.includes('_')){
                                if(currentResult.callBackId.includes('getConfig')){
                                    if(currentResult.callBackId == _this.getConfigCallBackId){
                                        window[currentResult.callBackId](currentResult.callParam);
                                    }else {
                                        //getConfig回包数据callBackId不匹配
                                        _this.webProcess({"process":'5'});
                                    }
                                }else {
                                    window[currentResult.callBackId](currentResult.callParam);
                                }
                            }else {
                                window[currentResult.callName](currentResult.callParam);
                            }
                        }else {
                            console.error('[MeshH5]', 'Imo backData Code非0');
                        }
                    }
                }
                return wrapped;
            };
            /**
             * 获取版本号(H5SDK 协议)
             * @returns {Promise<unknown>}
             */
            ImoSDK.prototype.getVersion = function (){
                return new Promise(function (resolve){
                    var data = "1.0.0";
                    resolve(data);
                });
            };

            /**
             * 游戏加载完毕
             * @returns {Promise<unknown>}
             */
            ImoSDK.prototype.gameLoaded = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'gameLoaded',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 吊起观众列表
             * @param params
             * @returns {Promise<unknown>}
             */
            ImoSDK.prototype.openAudiencePanel = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'openAudiencePanel',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 吊起玩家资料卡
             * @param params
             * openId标识 String {"openId":'xxxxx'}
             * @returns {Promise<unknown>}
             */
            ImoSDK.prototype.showUserCard = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'showUserCard',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 获取用户是否已关注
             * @param params
             * openIds List
             * {
             * "openIds":["user1openid", "user2openid"]
             * }
             * @returns {Promise<unknown>}
             * {
             * "openIds":{"user1openid":true, "user2openid":false} //true为已关注
             * }
             */
            ImoSDK.prototype.isFollow = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'isFollow',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 关注用户
             * @param params
             * openId标识 String {"openId":'xxxxx'}
             * @returns {Promise<unknown>}
             * 收到返回代表关注成功，处理按钮状态
             */
            ImoSDK.prototype.followUser = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'followUser',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 主动关闭游戏
             * 点击web的右上角关闭按钮时,app会有二次挽留弹窗
             * @returns {Promise<unknown>}
             */
            ImoSDK.prototype.exitGame = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'exitGame',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 销毁游戏
             * 异常情况直接关闭，网络或者封禁用户
             * @returns {Promise<unknown>}
             */
            ImoSDK.prototype.destroy = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'destroy',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 游戏加载进度关键节点
             * @param params
             *   * process String {"process":'0'}
             *     * '0' 代表成功拉起H5，开始加载
             *     * '1' 代表游戏资源加载完成
             *     * '2' 代表登录游戏服成功，真正进入到游戏主页
             *     * "3' 代表登录游戏服失败
             *     * '4' 百顺调用getConfig接口，调用一次客户端上报一次
             *     * '5' 代表getConfig的callBackId不相符
             *     * '6' getConfig延迟回包大于15s
             *     * '7' 获取路由失败;
             *     * '8':游戏登录超时(暂时不加)
             *     * '9' 代表客户端getConfig回包完成
             *     * '10' 代表游戏发起获取路由请求
             *     * '11' 代表游戏获取路由成功
             *     * '12' 代表游戏发起建立长连接请求
             *     * '13' 代表游戏成功建立长连接
             *     * '14' ws链接超时
             *     * '15' 发送登录消息
             *     * '16' 进入游戏Scene主入口
             */
            ImoSDK.prototype.webProcess = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'webProcess',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 获取用户信息配置
             * @returns {Promise<unknown>}
             * 返回用户信息
             */
            ImoSDK.prototype.getConfig = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'getConfig',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject,15000);
                });
            };

            /**
             * 提示余额不足，拉起充值商城
             * @returns {Promise<unknown>}
             */
            ImoSDK.prototype.gameRecharge = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'gameRecharge',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };
            /**
             * 结算完成，回到准备页
             * @returns {Promise<unknown>}
             */
            ImoSDK.prototype.nextGameRound = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'nextGameRound',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };
            /**
             * 设置是否拦截手势
             * 玩家拦截，观众不拦截
             *  {
             * "interrupt": 0， // 0 不拦截，1 拦截
             *  }
             * @returns {Promise<unknown>}
             */
            ImoSDK.prototype.setInterruptTouchEvent = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'setInterruptTouchEvent',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };
            /**
             * 设置游戏音量开关 可选，需要哪个传哪个 0 关 1 开
             * {
             *      "soundEffects": 0,
             *      "bgMusic": 0
             * }
             */
            ImoSDK.prototype.setGameSetting = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'setGameSetting',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 获取游戏音量开关 0 关 1 开
             * {
             *      "soundEffects": 0,
             *      "bgMusic": 0
             * }
             */
            ImoSDK.prototype.getGameSetting = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'getGameSetting',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 通知客户端游戏不支持webgl
             *  {
             *     "errorMsg": "This device does not support webgl"
             *  }
             */
            ImoSDK.prototype.unSupportDevice = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'unSupportDevice',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 是否支持分享功能
             * @returns
             * isSupport boolean
             * true 需要展示分享按钮&点击可调用openGameSharePanel
             */
            ImoSDK.prototype.isSupportShare = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'clientJSVersion',
                        'callParam':params ? params : {}
                    },function (data){
                        var isSupport = false;
                        if(Utils.isAndroid()){
                            if(data.android_version){
                                isSupport = parseInt(data.android_version) >= 1;
                            }
                        }else if(Utils.isIOS()){
                            if(data.iOS_version){
                                isSupport = parseInt(data.iOS_version) >= 1;
                            }
                        }
                        resolve({'isSupport':isSupport});
                    },reject);
                });
            };

            /**
             * 是否需要游戏背景图显示
             * @returns
             * isShow boolean
             * 默认为 false
             */
            ImoSDK.prototype.isShowGameBg = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'clientJSVersion',
                        'callParam':params ? params : {}
                    },function (data){
                        var isShow = false;
                        if(Utils.isAndroid()){
                            if(data.android_version){
                                isShow = parseInt(data.android_version) >= 100;
                            }
                        }else if(Utils.isIOS()){
                            if(data.iOS_version){
                                isShow = parseInt(data.iOS_version) >= 100;
                            }
                        }
                        resolve({'isShow':isShow});
                    },reject);
                });
            };

            /**
             * 吊起分享面板
             */
            ImoSDK.prototype.openGameSharePanel = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'openGameSharePanel',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };
            /**
             * 吊起客户端游戏规则弹窗
             */
            ImoSDK.prototype.openGameRuleDialog = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'openGameRuleDialog',
                        'callParam':params ? params : {}
                    },function (data){
                        resolve(data);
                    },reject);
                });
            };

            /**
             * 上报游戏行为 imo废弃，兼容保留而已
             * 游戏加载进度  type: 1, params: {progress: 10}
             * 游戏内加载页加载完成 type:2
             * 游戏房间号 type：3, params: {gameRoomId: 'xxxx'}
             * @returns {Promise<unknown>}
             */
            ImoSDK.prototype.sendGameAction = function (params) {

            };

            /**
            * 打开充值面板
            *  {
            *      "from":2003,  业务来源 百顺游戏
            *      "source"： 200301, 具体场景 200301：购买皮肤余额不足，200302：购买礼物余额不足
            *  }
            * @returns {Promise<unknown>}
            */
            ImoSDK.prototype.openRechargePanel = function (params) {
                var _this = this;
                return new Promise(function (resolve, reject) {
                    _this.request("sendMessageByJs", {
                        'callName': 'openRechargePanel',
                        'callParam': params ? params : {}
                    }, function (data) {
                        resolve(data);
                    }, reject);
                });
            };

             /**
             * 是否支持打开商城
             */
             ImoSDK.prototype.isSupportRecharge = function (params){
                var _this = this;
                return new Promise(function (resolve,reject){
                    _this.request("sendMessageByJs",{
                        'callName':'clientJSVersion',
                        'callParam':params ? params : {}
                    },function (data){
                        var isSupport = false;
                        if(Utils.isAndroid()){
                            if(data.android_version){
                                isSupport = parseInt(data.android_version) >= 2;
                            }
                        }else if(Utils.isIOS()){
                            if(data.iOS_version){
                                isSupport = parseInt(data.iOS_version) >= 2;
                            }
                        }
                        resolve({'isSupport':isSupport});
                    },reject);
                });
            };

            return ImoSDK;
        }());
        var imoSDK = new ImoSDK();
        exports.imoSDK = imoSDK;
        Object.defineProperty(exports, '__esModule', {value: true});
    }));