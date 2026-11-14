import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from './api'

const routes = [
  // ── Public website ──
  {
    path: '/',
    component: () => import('./layouts/SiteLayout.vue'),
    children: [
      {
        path: '',
        name: 'landing',
        component: () => import('./views/site/LandingView.vue'),
      },
      {
        path: 'how-to',
        name: 'how-to',
        component: () => import('./views/site/HowToView.vue'),
      },
      {
        path: 'pricing',
        name: 'pricing',
        component: () => import('./views/site/PricingView.vue'),
      },
      {
        path: 'privacy',
        name: 'privacy',
        component: () => import('./views/site/PrivacyView.vue'),
      },
      {
        path: 'terms',
        name: 'terms',
        component: () => import('./views/site/TermsView.vue'),
      },
      {
        path: 'about',
        name: 'about',
        component: () => import('./views/site/AboutView.vue'),
      },
    ],
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('./views/LoginView.vue'),
    meta: { guest: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('./views/LoginView.vue'),
    meta: { guest: true, register: true },
  },
  // ── Authenticated developer / admin console ──
  {
    path: '/console',
    component: () => import('./layouts/CloudLayout.vue'),
    meta: { auth: true },
    children: [
      { path: '', name: 'home', component: () => import('./views/HomeView.vue') },
      { path: 'apps', name: 'apps', component: () => import('./views/AppsView.vue') },
      { path: 'apps/:id', name: 'app', component: () => import('./views/ProjectView.vue') },
      {
        path: 'projects/:id',
        redirect: (to) => ({ name: 'app', params: { id: to.params.id } }),
      },
      { path: 'sdk', name: 'sdk', component: () => import('./views/SdkView.vue') },
      { path: 'packages', name: 'packages', component: () => import('./views/PackagesView.vue') },
      { path: 'billing', name: 'billing', component: () => import('./views/BillingView.vue') },
      {
        path: 'admin',
        name: 'admin',
        component: () => import('./views/AdminView.vue'),
        meta: { admin: true },
      },
      { path: 'seller', redirect: { name: 'admin' } },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to) {
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const token = getToken()
  if (to.matched.some((r) => r.meta.auth) && !token) {
    return { name: 'login', query: { next: to.fullPath } }
  }
  if (to.matched.some((r) => r.meta.admin) && token) {
    // admin gate also enforced in CloudLayout after profile load
  }
  if (to.meta.guest && token) return { name: 'home' }
  return true
})
