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
};
