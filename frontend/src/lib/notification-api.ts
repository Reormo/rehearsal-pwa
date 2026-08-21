import { request } from "@/lib/api";

export type AppNotification = {
  id: number;
  type: string;
  title: string;
  body: string;
  linkPath: string | null;
  readAt: string | null;
  createdAt: string;
};

export type UnreadNotificationCount = {
  count: number;
};

export type NotificationSettings = {
  rehearsalReminderMinutes: number | null;
  updatedAt: string;
};

export type PushConfig = {
  enabled: boolean;
  publicKey: string | null;
};

export type PushStatus = {
  activeSubscriptions: number;
};

export type PushTestResult = {
  activeSubscriptions: number;
  successCount: number;
  disabledCount: number;
};

export const notificationApi = {
  list() {
    return request<AppNotification[]>("/api/notifications");
  },

  unreadCount() {
    return request<UnreadNotificationCount>("/api/notifications/unread-count");
  },

  markAllRead() {
    return request<void>("/api/notifications/read-all", {
      method: "POST",
    });
  },

  dismiss(notificationId: number) {
    return request<void>(`/api/notifications/${notificationId}`, {
      method: "DELETE",
    });
  },

  settings() {
    return request<NotificationSettings>("/api/notifications/settings");
  },

  updateSettings(rehearsalReminderMinutes: number | null) {
    return request<NotificationSettings>("/api/notifications/settings", {
      method: "PUT",
      body: JSON.stringify({ rehearsalReminderMinutes }),
    });
  },

  pushConfig() {
    return request<PushConfig>("/api/notifications/push/config");
  },

  pushStatus() {
    return request<PushStatus>("/api/notifications/push/status");
  },

  subscribePush(input: {
    endpoint: string;
    p256dh: string;
    auth: string;
  }) {
    return request<void>("/api/notifications/push/subscription", {
      method: "PUT",
      body: JSON.stringify(input),
    });
  },

  unsubscribePush(endpoint: string) {
    return request<void>("/api/notifications/push/unsubscribe", {
      method: "POST",
      body: JSON.stringify({ endpoint }),
    });
  },

  testPush() {
    return request<PushTestResult>("/api/notifications/push/test", {
      method: "POST",
    });
  },
};
