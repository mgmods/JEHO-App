// firebase-config.js
// Firebase 配置（从控制台复制，不要泄露到 git 公开仓库）
const firebaseConfigDebug = {
  apiKey: "AIzaSyDxRBa4wdE1Ku-aP42hU9eaFxQT-qmwCB8",
  authDomain: "modularization-fruitpart-841f7.firebaseapp.com",
  projectId: "modularization-fruitpart-841f7",
  storageBucket: "modularization-fruitpart-841f7.firebasestorage.app",
  messagingSenderId: "766922631673",
  appId: "1:766922631673:web:fa0841c0c79a108614b528",
  measurementId: "G-J6DY2ZY112"
};
const firebaseConfigRelease = {
 apiKey: "AIzaSyDxRBa4wdE1Ku-aP42hU9eaFxQT-qmwCB8",
  authDomain: "modularization-fruitpart-841f7.firebaseapp.com",
  projectId: "modularization-fruitpart-841f7",
  storageBucket: "modularization-fruitpart-841f7.firebasestorage.app",
  messagingSenderId: "766922631673",
  appId: "1:766922631673:web:4c282459fce9479a14b528",
  measurementId: "G-V9RMKWGBBR"
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