import type { Announcement } from "@/lib/api";

export function announcementPreview(content: string, maxLength = 90) {
  const normalized = content.replace(/\s+/g, " ").trim();
  return normalized.length > maxLength
    ? `${normalized.slice(0, maxLength).trimEnd()}…`
    : normalized;
}

export function formatAnnouncementDate(value: Announcement["createdAt"]) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
