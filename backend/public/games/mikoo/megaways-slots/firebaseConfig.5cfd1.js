// firebase-config.js
// Firebase 配置（从控制台复制，不要泄露到 git 公开仓库）
const firebaseConfigDebug = {
    apiKey: "AIzaSyAne4iW6KaFXWj31nhKyyOd8tejDcvF4H4",
    authDomain: "modularization-megawayslots.firebaseapp.com",
    projectId: "modularization-megawayslots",
    storageBucket: "modularization-megawayslots.firebasestorage.app",
    messagingSenderId: "723598865520",
    appId: "1:723598865520:web:993f2fa5e2d8ee2062b0b5",
    measurementId: "G-2C2E73R2HY"
};
const firebaseConfigRelease = {
    apiKey: "AIzaSyAne4iW6KaFXWj31nhKyyOd8tejDcvF4H4",
    authDomain: "modularization-megawayslots.firebaseapp.com",
    projectId: "modularization-megawayslots",
    storageBucket: "modularization-megawayslots.firebasestorage.app",
    messagingSenderId: "723598865520",
    appId: "1:723598865520:web:d7ef66eba28165ea62b0b5",
    measurementId: "G-X7MHV78KHP"
};

window.FIREBASE_CONFIG = window._CCSettings.debug ? firebaseConfigDebug : firebaseConfigRelease;
// 建议同时暴露一个标志变量，便于其他脚本判断是否启用
window.USE_FIREBASE = true;

window.setupFirebase = function () {
    if(window._CCSettings.debug){
        console.log("使用 Firebase Debug 配置");
    }else{
        console.log("使用 Firebase Release 配置");
    }
    if (window.USE_FIREBASE && window.firebase) {
        console.log('Firebase 已就绪，开始初始化...' + window._CCSettings.debug);
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
        console.warn("Firebase 未就绪，5秒后重试...");
        setTimeout(window.setupFirebase, 5000);  // 延迟5秒重试一次
    }
}