import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "./stores/auth";
import LoginView from "./views/LoginView.vue";
import RegisterView from "./views/RegisterView.vue";
import MyInstanceView from "./views/MyInstanceView.vue";
import FileSpaceView from "./views/FileSpaceView.vue";
import ChatShellView from "./views/ChatShellView.vue";
import EdgeChatView from "./views/EdgeChatView.vue";
import AdminUsersView from "./views/AdminUsersView.vue";
import AdminInstancesView from "./views/AdminInstancesView.vue";
import AdminEdgeNodesView from "./views/AdminEdgeNodesView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: LoginView, meta: { public: true } },
    { path: "/register", component: RegisterView, meta: { public: true } },
    { path: "/", redirect: "/my-claw" },
    { path: "/chat", component: ChatShellView, meta: { requiresAuth: true } },
    { path: "/edge-chat", component: EdgeChatView, meta: { requiresAuth: true } },
    { path: "/files", component: FileSpaceView, meta: { requiresAuth: true } },
    { path: "/my-claw", component: MyInstanceView, meta: { requiresAuth: true } },
    { path: "/my-instance", redirect: "/my-claw" },
    { path: "/admin/users", component: AdminUsersView, meta: { requiresAuth: true, adminOnly: true } },
    { path: "/admin/instances", component: AdminInstancesView, meta: { requiresAuth: true, adminOnly: true } },
    { path: "/admin/edge-nodes", component: AdminEdgeNodesView, meta: { requiresAuth: true, adminOnly: true } }
  ]
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  await auth.ensureLoaded();

  if (to.meta.public) {
    if (auth.isAuthenticated && (to.path === "/login" || to.path === "/register")) {
      return "/my-claw";
    }
    return true;
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return "/login";
  }

  if (to.meta.adminOnly && !auth.isAdmin) {
    return "/my-claw";
  }

  return true;
});

export default router;
