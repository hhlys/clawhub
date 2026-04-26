import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "./stores/auth";
import LoginView from "./views/LoginView.vue";
import RegisterView from "./views/RegisterView.vue";
import MyInstanceView from "./views/MyInstanceView.vue";
import AdminUsersView from "./views/AdminUsersView.vue";
import AdminInstancesView from "./views/AdminInstancesView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: LoginView, meta: { public: true } },
    { path: "/register", component: RegisterView, meta: { public: true } },
    { path: "/", redirect: "/my-instance" },
    { path: "/my-instance", component: MyInstanceView, meta: { requiresAuth: true } },
    { path: "/admin/users", component: AdminUsersView, meta: { requiresAuth: true, adminOnly: true } },
    { path: "/admin/instances", component: AdminInstancesView, meta: { requiresAuth: true, adminOnly: true } }
  ]
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  await auth.ensureLoaded();

  if (to.meta.public) {
    if (auth.isAuthenticated && (to.path === "/login" || to.path === "/register")) {
      return auth.isAdmin ? "/admin/instances" : "/my-instance";
    }
    return true;
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return "/login";
  }

  if (to.meta.adminOnly && !auth.isAdmin) {
    return "/my-instance";
  }

  return true;
});

export default router;
