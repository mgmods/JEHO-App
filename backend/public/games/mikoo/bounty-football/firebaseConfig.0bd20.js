// firebase-config.js
// Firebase 配置（从控制台复制，不要泄露到 git 公开仓库）
const firebaseConfigDebug = {
  apiKey: "AIzaSyAmlloJVKCueJr0uPTSsvCeK6wP5Gl5BjM",
  authDomain: "modularization-bountyfootball.firebaseapp.com",
  projectId: "modularization-bountyfootball",
  storageBucket: "modularization-bountyfootball.firebasestorage.app",
  messagingSenderId: "857438223406",
  appId: "1:857438223406:web:5a0f4317bd0b4bd7cbf3bf",
  measurementId: "G-0SL03LLCH3"
};
const firebaseConfigRelease = {
apiKey: "AIzaSyAmlloJVKCueJr0uPTSsvCeK6wP5Gl5BjM",
  authDomain: "modularization-bountyfootball.firebaseapp.com",
  projectId: "modularization-bountyfootball",
  storageBucket: "modularization-bountyfootball.firebasestorage.app",
  messagingSenderId: "857438223406",
  appId: "1:857438223406:web:90c449edd47c5188cbf3bf",
  measurementId: "G-B5J4L0GZ1X"
};

window.FIREBASE_CONFIG = window._CCSettings.debug?firebaseConfigDebug:firebaseConfigRelease;
// 建议同时暴露一个标志变量，便于其他脚本判断是否启用
window.USE_FIREBASE = true;

window.setupFirebase = function(){
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
        setTimeout(window.setupFirebase, 5000);
    }
}