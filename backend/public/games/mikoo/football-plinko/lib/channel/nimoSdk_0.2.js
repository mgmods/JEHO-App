(function (global, factory) {
   typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports) :
       typeof define === 'function' && define.amd ? define(['exports'], factory) :
           (global = typeof globalThis !== 'undefined' ? globalThis : global || self, factory(global.NimoGameSdk = {}));
})(this, (function (exports) { 'use strict';

   function pay(payParams) {
      return callWithReturn('pay', payParams);
   }
   function login() {
      return callWithReturn('login');
   }
   function lockOrientation(params) {
      call('lockOrientation', params);
   }
   var hasAuth = false;
   function getAuthorization(authParams) {
      var ret = callWithReturn('getAuthorization', authParams);
      ret.then(function (_a) {
         var payload = _a.payload, status = _a.status;
         if (status === 0 && payload && payload.authorization) {
            hasAuth = true;
         }
      });
      return ret;
   }
   function init() {
      var isNimoTv = !!getParentOrigin();
      if (isNimoTv) {
         call('reportShow', { eventType: 'init', hasAuth: false });
         var isShow_1 = true;
         var handleHide_1 = function (event) {
            if (isShow_1) {
               isShow_1 = false;
               call('reportHide', { eventType: event.type });
            }
         };
         var handleShow_1 = function (event) {
            if (!isShow_1) {
               isShow_1 = true;
               call('reportShow', { eventType: event.type, hasAuth: hasAuth });
            }
         };
         window.addEventListener('pagehide', handleHide_1);
         window.addEventListener('pageshow', handleShow_1);
         window.addEventListener('visibilitychange', function (evt) {
            if (document.visibilityState === 'hidden') {
               handleHide_1(evt);
            }
            else {
               handleShow_1(evt);
            }
         });
         window.addEventListener('beforeunload', handleHide_1);
      }
      return isNimoTv;
   }
   var _sessionId;
   function getSessionId() {
      if (_sessionId === undefined) {
         _sessionId = "".concat(Date.now(), "_").concat(Math.random() * 1e8);
      }
      return _sessionId;
   }
   var _id = 0;
   function getMessageId() {
      _id += 1;
      return _id;
   }
   var _parentOrigin;
   function isSafeOrigin(origin) {
      return origin === 'https://act.nimo.tv';
   }
   function getParentOrigin() {
      if (_parentOrigin === undefined) {
         if (window.top === window) {
            _parentOrigin = '';
            return '';
         }
         var referrer = document.referrer;
         var __originReferrer = getOrigin(referrer);
         if (__originReferrer && isSafeOrigin(__originReferrer)) {
            _parentOrigin = __originReferrer;
            sessionStorage.setItem('nimo-game-sdk-origin', __originReferrer);
            return _parentOrigin;
         }
         var __originSession = sessionStorage.getItem('nimo-game-sdk-origin');
         if (__originSession && isSafeOrigin(__originSession)) {
            _parentOrigin = __originSession;
            return _parentOrigin;
         }
         _parentOrigin = '';
      }
      return _parentOrigin;
   }
   function getOrigin(url) {
      var result = /^https:\/\/[^/?#]+/.exec(url);
      return (result && result[0]) || '';
   }
   var callbacks = {};
   var _addedEvent = false;
   function addMessageEvent() {
      if (!_addedEvent) {
         _addedEvent = true;
         window.addEventListener('message', function (event) {
            if (event.origin !== getParentOrigin()) {
               return;
            }
            var _a = event.data || {}, id = _a.id, result = _a.result, session = _a.session;
            if ((session === getSessionId(),
            id &&
            Object.prototype.hasOwnProperty.call(callbacks, id) &&
            callbacks[id])) {
               var fn = callbacks[id];
               delete callbacks[id];
               fn(result);
            }
         });
      }
   }
   function callWithReturn(func, params) {
      if (!getParentOrigin()) {
         return Promise.reject(new Error('not in nimo tv iframe'));
      }
      addMessageEvent();
      var id = getMessageId();
      var ret = new Promise(function (resolve) {
         callbacks[id] = resolve;
      });
      postMessage(func, id, params);
      return ret;
   }
   function call(func, params) {
      postMessage(func, getMessageId(), params);
   }
   function postMessage(type, id, params) {
      var data = {
         source: 'nimo-game-sdk',
         type: type,
         id: id,
         session: getSessionId(),
         params: params
      };
      window.parent.postMessage(data, getParentOrigin());
   }

   exports.getAuthorization = getAuthorization;
   exports.init = init;
   exports.lockOrientation = lockOrientation;
   exports.login = login;
   exports.pay = pay;

}));