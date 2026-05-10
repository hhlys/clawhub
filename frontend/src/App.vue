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

  return [
    { label: "对话", to: "/chat", icon: "chat" },
    { label: "云边对话", to: "/edge-chat", icon: "edge" },
    { label: "我的龙虾", to: "/my-claw", icon: "claw" },
    { label: "文件", to: "/files", icon: "file" }
  ];
});

async function handleLogout() {
  await auth.logout();
  router.push("/login");
}
</script>

<template>
  <div class="app-shell" :class="{ 'auth-shell': !auth.user }">
    <template v-if="auth.user">
      <header class="global-header">
        <router-link class="brand" to="/my-claw">
          <span class="brand-mark"></span>
          <span>龙虾控制台</span>
        </router-link>

        <div class="user-menu">
          <div class="user-avatar">{{ auth.user.username.slice(0, 1).toUpperCase() }}</div>
          <div class="user-meta">
            <strong>{{ auth.user.username }}</strong>
            <span>{{ auth.user.role === "ADMIN" ? "管理员" : "普通用户" }}</span>
          </div>
          <button class="ghost-button" @click="handleLogout">退出</button>
        </div>
      </header>

      <div class="workspace-layout">
        <aside class="rail">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="rail-link"
            active-class="active"
          >
            <span class="rail-icon" :class="`icon-${item.icon}`"></span>
            <span>{{ item.label }}</span>
          </router-link>
        </aside>

        <main class="page-host">
          <router-view />
        </main>
      </div>
    </template>

    <main v-else class="auth-host">
      <router-view />
    </main>
  </div>
</template>
