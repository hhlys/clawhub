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

  if (auth.user.role === "ADMIN") {
    return [
      { label: "Instances", to: "/admin/instances" },
      { label: "Users", to: "/admin/users" }
    ];
  }

  return [{ label: "My Instance", to: "/my-instance" }];
});

async function handleLogout() {
  await auth.logout();
  router.push("/login");
}
</script>

<template>
  <div class="app-frame">
    <header class="topbar">
      <div>
        <p class="eyebrow">ClawHub Control Plane</p>
        <h1>Multi-instance management for QwenPaw and beyond.</h1>
      </div>
      <div v-if="auth.user" class="userbox">
        <div>
          <strong>{{ auth.user.username }}</strong>
          <span>{{ auth.user.role }}</span>
        </div>
        <button class="btn btn-soft" @click="handleLogout">Logout</button>
      </div>
    </header>

    <div class="layout">
      <aside v-if="auth.user" class="sidebar">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-link"
          active-class="active"
        >
          {{ item.label }}
        </router-link>
      </aside>

      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>
