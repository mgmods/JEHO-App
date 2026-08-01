// // 测试环境配置
// const sentryConfigDebug = {
//     dsn: 'https://3493f92061a997d97f586b82c3c65218@o4510661281185792.ingest.us.sentry.io/4510673578164224',// 此参数以游戏为区分，每个游戏会不一样，目前这默认是伽罗宝石的dsn
//     // tunnel: '你的tunnel地址',
//     tracesSampleRate: 1.0,//此参数用于设置性能跟踪的采样率，范围从 0.0 到 1.0。它决定了多少比例的事务（比如用户操作、请求等）会被发送到 Sentry 进行性能监控。
//     replaysSessionSampleRate: 0.1,//此参数用于设置录制会话重放的采样率，范围同样是 0.0 到 1.0。它决定了多少比例的用户会话会被录制并发送到 Sentry
//     replaysOnErrorSampleRate: 1.0,//此参数设置在发生错误时，录制会话重放的采样率。它决定了在发生错误时，多少比例的会话会被录制并发送到 Sentry
// }

// 生产环境配置
const sentryConfigRelease = {
    dsn: 'https://5e34321d4b651389937f70e6290c35ed@sentry.vchat-onlie.com:443/68',// 此参数以游戏为区分，每个游戏会不一样，目前这默认是伽罗宝石的dsn
    // tunnel: '你的tunnel地址',
    tracesSampleRate: 1.0,
    replaysSessionSampleRate: 0.1,
    replaysOnErrorSampleRate: 1.0,
}

// false 为测试环境 true 为线上环境
// window.SENTRY_CONFIG = window._CCSettings.debug ? sentryConfigDebug : sentryConfigRelease;
window.SENTRY_CONFIG = sentryConfigRelease;

// 是否启用 Sentry，便于其他脚本判断是否启用
window.USE_SENTRY = true;

window.setupSentry = function () {
    if (window.USE_SENTRY && window.Sentry) {
        console.log('Sentry 已就绪，开始初始化...');
        window.Sentry.init({
            dsn: window.SENTRY_CONFIG.dsn,
            // tunnel: window.SENTRY_CONFIG.tunnel,
            integrations: [
                new window.Sentry.Integrations.BrowserTracing(),
                new window.Sentry.Replay({ maskAllText: false }),
            ],
            tracesSampleRate: window.SENTRY_CONFIG.tracesSampleRate,
            replaysSessionSampleRate: window.SENTRY_CONFIG.replaysSessionSampleRate,
            replaysOnErrorSampleRate: window.SENTRY_CONFIG.replaysOnErrorSampleRate,
            beforeSend(event) {
                // if (event.level === 'log' || event.level === 'info') return null;
                event.extra = { gameVersion: '1.0.0', cocosVersion: cc.ENGINE_VERSION };
                return event;
            },
        });


        //全局错误捕获：通过这两个事件处理程序，基本确保捕获到应用程序中的大多数错误，无论是同步的 JavaScript 错误还是异步的 Promise 拒绝
        window.onerror = (msg, url, line, col, error) => {
            if (window.USE_SENTRY && window.Sentry) {
                window.Sentry.captureException(error);
            }
        };

        window.addEventListener('unhandledrejection', (e) => {
            if (window.USE_SENTRY && window.Sentry) {
                window.Sentry.captureException(e.reason);
            }
        });
    } else {
        console.warn('Sentry 未就绪，5秒后重试...');
        setTimeout(window.setupSentry, 5000);
    }
};