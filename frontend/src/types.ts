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

export interface Capacity {
  instanceLimit: number;
  currentInstanceCount: number;
  remainingInstanceSlots: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
