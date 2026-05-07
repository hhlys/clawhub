<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "./stores/auth";

const auth = useAuthStore();
const router = useRouter();

const navItems = computed(() => {
  if (!auth.user) {
    return [];
  }

  return [{ label: "我的Claw", to: "/my-instance" }];
});

const adminQuickLinks = computed(() => {
  if (auth.user?.role !== "ADMIN") {
    return [];
  }

  return [
    { label: "Edge Nodes", to: "/admin/edge-nodes" },
    { label: "实例总览", to: "/admin/instances" },
    { label: "用户管理", to: "/admin/users" }
  ];
});

async function handleLogout() {
  await auth.logout();
  router.push("/login");
}
</script>

<template>
  <div class="app-frame">
    <div class="layout" :class="{ 'auth-layout': !auth.user }">
      <aside v-if="auth.user" class="sidebar">
        <div class="sidebar-brand">
          <p class="eyebrow">我的AI云平台</p>
          <h1>我的AI云平台</h1>
          <p class="muted">统一管理你的专属 Claw 实例。</p>
        </div>
        <nav class="sidebar-nav">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="nav-link"
            active-class="active"
          >
            {{ item.label }}
          </router-link>
        </nav>
      </aside>

      <main class="main-content">
        <header v-if="auth.user" class="topbar">
          <div>
            <p class="eyebrow">我的AI云平台</p>
            <h2 class="page-brand">欢迎回来，{{ auth.user.username }}</h2>
          </div>
          <div class="userbox">
            <div v-if="adminQuickLinks.length" class="topbar-links">
              <router-link
                v-for="item in adminQuickLinks"
                :key="item.to"
                :to="item.to"
                class="topbar-link"
              >
                {{ item.label }}
              </router-link>
            </div>
            <div>
              <strong>{{ auth.user.username }}</strong>
              <span>{{ auth.user.role === "ADMIN" ? "管理员" : "普通用户" }}</span>
            </div>
            <button class="btn btn-soft" @click="handleLogout">退出登录</button>
          </div>
        </header>

        <router-view />
      </main>
    </div>
  </div>
</template>
