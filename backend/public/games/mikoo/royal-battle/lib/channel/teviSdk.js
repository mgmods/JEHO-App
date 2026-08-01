var TeviJS = (function (window) {
   var navigator = window.navigator,
       version = '1.0.0',
       userAgent = navigator.userAgent || navigator.vendor || window.opera,
       JSCallFuncCallbacks = {},
       actionMap = {
          checkRemainingUser: 'action.user.core.checkRemainingUser',
          getUserInfo: 'action.user.core.getInfo',
          buyItem: 'action.user.billy.buyItem',
          topup: 'action.user.billy.topup',
          purchaseStar: 'action.purchaseStar',
          executeLink: 'action.executeLink',
          quitGame: 'action.quitGame',
       }

   function isFunction(value) {
      return typeof value === 'function'
   }

   function isObject(obj) {
      return typeof obj === 'object'
   }

   function isEmpty(str) {
      return !str || 0 === str.trim().length
   }

   function isString(obj) {
      if (typeof obj === 'string') {
         return true
      }
      if (obj === undefined || obj === null) {
         return false
      }
      if (typeof obj !== 'object') {
         return false
      }
      if (!obj.constructor) {
         return false
      }
      return obj.constructor.toString().match(/string/i) !== null
   }

   var device = (function (agent) {
      var _agent = agent.toLowerCase()
      return {
         isWP: /iemobile/.test(_agent),
         isAndroid: /android/i.test(_agent) && !/iemobile/.test(_agent),
         isIOS: /iphone|ipad|ipod/.test(_agent) && !/iemobile/.test(_agent),
         isMobile: /android|iphone|ipad|ipod|iemobile/.test(_agent),
      }
   })(userAgent)

   var browser = (function (userAgent) {
      var b = userAgent.toLowerCase()
      return {
         webkit: /(webkit|khtml)/.test(b),
         opera: /opera/.test(b),
         msie: /msie/.test(b) && !/opera/.test(b),
         mozilla: /mozilla/.test(b) && !/(compatible|webkit)/.test(b),
      }
   })(userAgent)

   function parseJSON(str) {
      var base64Matcher = new RegExp(
          '^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{4})$',
      )
      if (isObject(JSON) && JSON.parse && isString(str) && base64Matcher.test(str)) {
         return JSON.parse(atob(str))
      } else if (isObject(JSON) && JSON.parse && isString(str)) {
         return JSON.parse(str)
      } else if (typeof str === 'object') {
         return str
      } else {
         return {}
      }
   }

   function onJSCall(data) {
      console.log('onJSCall', JSON.stringify(data))
      try {
         data = parseJSON(data)
         if (JSCallFuncCallbacks[data.action]) {
            var callback = JSCallFuncCallbacks[data.action]
            callback.call(this, data)
            delete JSCallFuncCallbacks[data.action]
         }
      } catch (e) {
         console.error('TeviJS onJSCall Error', e)
      }
   }

   function defaulCallBackJSCall(data) {
      if (data !== null && data !== undefined) {
         data = parseJSON(data)
      }
   }

   function getUserAgent() {
      try {
         return window.navigator.userAgent
      } catch (e) {}
      return ''
   }

   function serialize(data) {
      if (isObject(JSON) && JSON.stringify) {
         return JSON.stringify(data)
      }
      if (data === undefined) {
         return 'undefined'
      }
      if (data === null) {
         return 'null'
      }
      try {
         if (typeof data === 'string' || data.constructor.toString().match(/string/i) !== null) {
            return '"' + data.replace(/"/g, '\\"') + '"'
         }
      } catch (e) {}
      var a
      if (Object.prototype.toString.call(data).match(/array/i) !== null) {
         a = []
         var length = data.length
         for (var i = 0; i < length; i++) {
            a.push(serialize(data[i]))
         }
         return '[' + a.join(',') + ']'
      }
      if (typeof data === 'object') {
         a = []
         for (var f in data) {
            a.push('"' + f + '":' + serialize(data[f]))
         }
         return '{' + a.join(',') + '}'
      }
      return data.toString()
   }

   window.addEventListener('message', function (event) {
      // origin event.origin
      // TODO: check the same domain (tevi.com, tevi.dev)

      // Message received from parent
      if (event?.data) {
         if (event?.data?.action) {
            console.log('Message received from the parent: ' + JSON.stringify(event.data))
            onJSCall(event.data)
         }
      }
   })

   function jsCall(action, options, callback) {
      console.log('JSCall', action, options, callback)
      JSCallFuncCallbacks[action] = callback

      if (options === undefined || options === null) {
         options = {}
      }

      if (isFunction(options) && callback === undefined) {
         callback = options
         options = {}
      }

      if (!callback) {
         callback = TeviJS.defaultCallback
      }

      var dataCallback = {
         error_code: -14,
         error_message: 'request Timeout!',
         data: {},
         action: action,
      }

      try {
         options = serialize(options)
         console.log('options', options, 'iOS', device.isIOS, 'android', device.isAndroid)
         if (device.isIOS) {
            try {
               if (webkit?.messageHandlers?.TeviJSInterface)
                  return webkit?.messageHandlers?.TeviJSInterface.postMessage({
                     action,
                     options,
                  })
            } catch (error) {
               console.log(error)
            }
         }
         if (device.isAndroid) {
            try {
               if (isObject(TeviJSInterface)) {
                  return TeviJSInterface.jsCall(action, options)
               }
            } catch (error) {
               console.log(error)
            }
         }

         // Browser
         console.log('Not mobile')

         if (window.top) {
            window.top.postMessage({ action, options }, '*')
            return
         }

         dataCallback = {
            error_code: -6,
            error_message: 'Not Available Device!',
            data: {
               userAgent: getUserAgent(),
               options: options,
            },
            action: action,
         }
         TeviJS.onJSCall(dataCallback)
      } catch (E) {
         console.log('jsCall error', E)
         dataCallback = {
            error_code: -5,
            error_message: 'Not ready!',
            data: {
               userAgent: getUserAgent(),
               options: options,
            },
            action: action,
            js_error: E,
         }
         TeviJS.onJSCall(dataCallback)
         return false
      }
   }

   function getUserInfo(options, callback) {
      return jsCall(actionMap.getUserInfo, options, callback)
   }

   function checkRemainingUser(options, callback) {
      return jsCall(actionMap.checkRemainingUser, options, callback)
   }

   function topup(options, callback) {
      return jsCall(actionMap.topup, options, callback)
   }

   function buyItem(options, callback) {
      return jsCall(actionMap.buyItem, options, callback)
   }

   function purchaseStar(options, callback) {
      return jsCall(actionMap.purchaseStar, options, callback)
   }

   function executeLink(options, callback) {
      return jsCall(actionMap.executeLink, options, callback)
   }

   function quitGame(options, callback) {
      return jsCall(actionMap.quitGame, options, callback)
   }

   return {
      version,
      jsCall,
      checkRemainingUser,
      topup,
      getUserInfo,
      buyItem,
      purchaseStar,
      executeLink,
      quitGame,
      browser,
      onJSCall,
      defaultCallback: defaulCallBackJSCall,
   }
})(window)