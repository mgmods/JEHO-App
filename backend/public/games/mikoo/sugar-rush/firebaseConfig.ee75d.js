// firebase-config.js
// Firebase 配置（从控制台复制，不要泄露到 git 公开仓库）
const firebaseConfigRelease = {
 apiKey: "AIzaSyBe-0lM1Oe4CjJr7S0Bx6GpvN-JWIIaFkM",
  authDomain: "modularization-sugarrush.firebaseapp.com",
  projectId: "modularization-sugarrush",
  storageBucket: "modularization-sugarrush.firebasestorage.app",
  messagingSenderId: "786140055461",
  appId: "1:786140055461:web:56f9da7a0d9eae3b568a4c",
  measurementId: "G-XZ6C2MY214"
};
const firebaseConfigDebug = {
  apiKey: "AIzaSyBe-0lM1Oe4CjJr7S0Bx6GpvN-JWIIaFkM",
  authDomain: "modularization-sugarrush.firebaseapp.com",
  projectId: "modularization-sugarrush",
  storageBucket: "modularization-sugarrush.firebasestorage.app",
  messagingSenderId: "786140055461",
  appId: "1:786140055461:web:5cb68ccac037789c568a4c",
  measurementId: "G-C9KNQJTS1D"
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
    console.warn("Firebase 未就绪，5秒后重试...");
    setTimeout(initFirebase, 5000);  // 延迟5秒重试一次
  }
}