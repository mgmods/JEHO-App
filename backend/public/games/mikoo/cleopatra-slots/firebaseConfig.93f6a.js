// firebase-config.js
// Firebase 配置（从控制台复制，不要泄露到 git 公开仓库）
const firebaseConfigDebug = {
    apiKey: "AIzaSyBzxjnc8E3fkynh1hPfP-iZ92XO2bJSDcU",
    authDomain: "modularization-olympians.firebaseapp.com",
    projectId: "modularization-olympians",
    storageBucket: "modularization-olympians.firebasestorage.app",
    messagingSenderId: "630181507710",
    appId: "1:630181507710:web:1b37432aae22cd68f85e2f",
    measurementId: "G-TL8Z8Y59Z6"
};
const firebaseConfigRelease = {
    apiKey: "AIzaSyBzxjnc8E3fkynh1hPfP-iZ92XO2bJSDcU",
    authDomain: "modularization-olympians.firebaseapp.com",
    projectId: "modularization-olympians",
    storageBucket: "modularization-olympians.firebasestorage.app",
    messagingSenderId: "630181507710",
    appId: "1:630181507710:web:d1380fab526c7f51f85e2f",
    measurementId: "G-9359Y3V09E"
};

window.FIREBASE_CONFIG = window._CCSettings.debug ? firebaseConfigDebug : firebaseConfigRelease;
// 建议同时暴露一个标志变量，便于其他脚本判断是否启用
window.USE_FIREBASE = true;

window.setupFirebase = function () {
    if (window.USE_FIREBASE && window.firebase) {
        try {
            window.firebase.initializeApp(window.FIREBASE_CONFIG);
            const analytics = window.firebase.analytics();
            console.log("Firebase Analytics 初始化成功");
            analytics.setUserProperties({
                platform: "web",
            });
            // 初始化成功后立即上报游戏启动事件
            analytics.logEvent("Loading");
        } catch (e) {
            console.error("Firebase 初始化失败", e);
        }
    } else {
        // 添加重试计数，避免无限循环
        window._firebaseRetryCount = (window._firebaseRetryCount || 0) + 1;
        if (window._firebaseRetryCount > 3) {
            console.error("Firebase SDK 未就绪，放弃重试");
            return;
        }
        console.warn(`Firebase 未就绪，5 秒后重试... (${window._firebaseRetryCount}/3)`);
    }
}