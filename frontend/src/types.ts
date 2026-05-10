export type UserRole = "ADMIN" | "USER";
export type UserStatus = "ACTIVE" | "DISABLED";
export type InstanceStatus = "PENDING" | "RUNNING" | "STOPPED" | "FAILED" | "DELETED";
export type ProductType = "QWENPAW" | "OPENCLAW" | "OTHER";

export interface User {
  id: number;
  username: string;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AuthResponse {
  user: User;
}

export interface ManagedInstance {
  id: number;
  ownerUserId: number;
  ownerUsername: string;
  productType: ProductType;
  instanceName: string;
  containerName: string;
  dockerImage: string;
  host: string;
  hostPort: number;
  containerPort: number;
  dataVolumeHostPath: string;
  dataVolumeContainerPath: string;
  publicBaseUrl: string | null;
  entryUrl: string;
  status: InstanceStatus;
  createdAt: string;
  updatedAt: string;
}

export interface InstanceChatSession {
  id: number;
  instanceId: number;
  qwenpawSessionId: string;
  title: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  lastMessageAt: string;
}

export interface InstanceChatMessage {
  id: number;
  sessionId: number;
  role: "user" | "assistant" | "system";
  content: string;
  sequenceNo: number;
  createdAt: string;
}

export interface EdgeNode {
  id: number;
  nodeId: string;
  tenantId: string;
  groupName: string;
  username: string;
  hostIp: string | null;
  port: number | null;
  osName: string | null;
  arch: string | null;
  qwenpawVersion: string | null;
  capabilities: string[];
  metadata: Record<string, unknown>;
  status: "online" | "offline";
  lastSeenAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface EdgeChatNode {
  id: number;
  nodeId: string;
  tenantId: string;
  groupName: string;
  username: string;
  hostIp: string | null;
  port: number | null;
  osName: string | null;
  arch: string | null;
  qwenpawVersion: string | null;
  capabilities: string[];
  metadata: Record<string, unknown>;
  status: "online" | "offline";
  lastSeenAt: string;
}

export interface EdgeChatSession {
  id: number;
  nodeId: string;
  nodeName: string;
  conversationId: string;
  title: string;
  kind: "chat" | "task";
  status: string;
  createdAt: string;
  updatedAt: string;
  lastMessageAt: string;
}

export interface EdgeChatMessage {
  id: number;
  sessionId: number;
  role: "user" | "assistant" | "system";
  content: string;
  sequenceNo: number;
  createdAt: string;
}

export interface EdgeTaskEvent {
  id: number;
  taskId: string;
  conversationId: string;
  sequence: number;
  eventType: string;
  content: string | null;
  rawEvent: Record<string, unknown>;
  createdAt: string;
}

export interface Capacity {
  instanceLimit: number;
  currentInstanceCount: number;
  remainingInstanceSlots: number;
}

export interface ProvisioningProfile {
  defaultHost: string;
  containerPort: number;
  nextAvailableHostPort: number;
  usedHostPorts: number[];
  defaultProductVersion: string;
  supportedProductVersions: string[];
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
