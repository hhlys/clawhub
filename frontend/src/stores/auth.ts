import { defineStore } from "pinia";
import { apiRequest } from "../api";
import type { AuthResponse, User } from "../types";

export const useAuthStore = defineStore("auth", {
  state: () => ({
    user: null as User | null,
    loading: false,
    initialized: false
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.user),
    isAdmin: (state) => state.user?.role === "ADMIN"
  },
  actions: {
    async ensureLoaded() {
      if (this.initialized) {
        return;
      }
      this.loading = true;
      try {
        const response = await apiRequest<AuthResponse>("/api/auth/me");
        this.user = response.user;
      } catch {
        this.user = null;
      } finally {
        this.initialized = true;
        this.loading = false;
      }
    },
    async login(username: string, password: string) {
      this.loading = true;
      try {
        const response = await apiRequest<AuthResponse>("/api/auth/login", {
          method: "POST",
          bodyJson: { username, password }
        });
        this.user = response.user;
        this.initialized = true;
      } finally {
        this.loading = false;
      }
    },
    async register(username: string, password: string) {
      this.loading = true;
      try {
        await apiRequest<AuthResponse>("/api/auth/register", {
          method: "POST",
          bodyJson: { username, password }
        });
      } finally {
        this.loading = false;
      }
    },
    async logout() {
      await apiRequest<void>("/api/auth/logout", { method: "POST" });
      this.user = null;
      this.initialized = true;
    }
  }
});
