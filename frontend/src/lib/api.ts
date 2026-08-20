export type ClubRole = "MEMBER" | "ADMIN" | "SUPER_ADMIN";
export type SignupStatus = "PENDING" | "APPROVED" | "REJECTED";
export type SongStatus = "ACTIVE" | "ARCHIVED";

export type AuthUser = {
  id: number;
  loginId: string;
  name: string;
  clubId: number;
  role: ClubRole;
};

export type SignupApplication = {
  id: number;
  loginId: string;
  name: string;
  status: SignupStatus;
  reviewedBy: number | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
};

export type Member = {
  userId: number;
  loginId: string | null;
  name: string;
  role: ClubRole;
};

export type InviteCode = {
  code: string;
  createdAt: string;
};

export type SongMember = {
  userId: number;
  loginId: string | null;
  name: string;
  sessionName: string;
  leader: boolean;
};

export type Song = {
  id: number;
  title: string;
  status: SongStatus;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  members: SongMember[];
};

export type Announcement = {
  id: number;
  title: string;
  content: string;
  pinned: boolean;
  authorUserId: number | null;
  authorName: string;
  createdAt: string;
  updatedAt: string;
};

export type AdminActionLog = {
  id: number;
  actorUserId: number | null;
  actorName: string;
  actionType: string;
  targetType: string;
  targetId: number | null;
  reason: string | null;
  beforeData: Record<string, unknown> | null;
  afterData: Record<string, unknown> | null;
  createdAt: string;
};

type ErrorPayload = {
  code?: string;
  message?: string;
  fields?: Record<string, string>;
};

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ??
  "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  code: string;
  fields: Record<string, string>;

  constructor(
    status: number,
    code: string,
    message: string,
    fields: Record<string, string> = {},
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

let refreshPromise: Promise<AuthUser> | null = null;

async function parseError(response: Response): Promise<ApiError> {
  let payload: ErrorPayload = {};
  try {
    payload = (await response.json()) as ErrorPayload;
  } catch {
    // The backend can return an empty response for security/filter failures.
  }

  return new ApiError(
    response.status,
    payload.code ?? `HTTP_${response.status}`,
    payload.message ?? "요청을 처리하지 못했습니다.",
    payload.fields ?? {},
  );
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
  retryAfterRefresh = true,
): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });

  const canRefresh =
    retryAfterRefresh &&
    response.status === 401 &&
    path !== "/api/auth/login" &&
    path !== "/api/auth/signup" &&
    path !== "/api/auth/refresh";

  if (canRefresh) {
    if (!refreshPromise) {
      refreshPromise = request<AuthUser>(
        "/api/auth/refresh",
        { method: "POST" },
        false,
      ).finally(() => {
        refreshPromise = null;
      });
    }

    await refreshPromise;
    return request<T>(path, init, false);
  }

  if (!response.ok) {
    throw await parseError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}

export const authApi = {
  signup(input: {
    inviteCode: string;
    loginId: string;
    password: string;
    name: string;
  }) {
    return request<{ applicationId: number; status: SignupStatus }>(
      "/api/auth/signup",
      { method: "POST", body: JSON.stringify(input) },
      false,
    );
  },

  login(input: { loginId: string; password: string }) {
    return request<AuthUser>(
      "/api/auth/login",
      { method: "POST", body: JSON.stringify(input) },
      false,
    );
  },

  me() {
    return request<AuthUser>("/api/auth/me");
  },

  refresh() {
    return request<AuthUser>(
      "/api/auth/refresh",
      { method: "POST" },
      false,
    );
  },

  logout() {
    return request<void>("/api/auth/logout", { method: "POST" }, false);
  },

  deleteMe(currentPassword: string) {
    return request<void>("/api/auth/me", {
      method: "DELETE",
      body: JSON.stringify({ currentPassword }),
    });
  },
};

export const adminApi = {
  signupApplications(status: SignupStatus = "PENDING") {
    return request<SignupApplication[]>(
      `/api/admin/signup-applications?status=${encodeURIComponent(status)}`,
    );
  },

  approveSignup(applicationId: number) {
    return request<Member>(
      `/api/admin/signup-applications/${applicationId}/approve`,
      { method: "POST" },
    );
  },

  rejectSignup(applicationId: number, reason: string) {
    return request<void>(
      `/api/admin/signup-applications/${applicationId}/reject`,
      { method: "POST", body: JSON.stringify({ reason }) },
    );
  },

  inviteCode() {
    return request<InviteCode>("/api/admin/invite-code");
  },

  rotateInviteCode() {
    return request<InviteCode>("/api/admin/invite-code/rotate", {
      method: "POST",
    });
  },

  members() {
    return request<Member[]>("/api/admin/members");
  },

  changeRole(userId: number, role: ClubRole) {
    return request<Member>(`/api/admin/members/${userId}/role`, {
      method: "PATCH",
      body: JSON.stringify({ role }),
    });
  },

  resetPassword(userId: number, newPassword: string) {
    return request<void>(`/api/admin/members/${userId}/password`, {
      method: "PATCH",
      body: JSON.stringify({ newPassword }),
    });
  },

  deleteMember(userId: number) {
    return request<void>(`/api/admin/members/${userId}`, {
      method: "DELETE",
    });
  },

  announcements() {
    return request<Announcement[]>("/api/admin/announcements");
  },

  createAnnouncement(input: { title: string; content: string; pinned: boolean }) {
    return request<Announcement>("/api/admin/announcements", {
      method: "POST",
      body: JSON.stringify(input),
    });
  },

  updateAnnouncement(
    announcementId: number,
    input: { title: string; content: string; pinned: boolean },
  ) {
    return request<Announcement>(`/api/admin/announcements/${announcementId}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
  },

  deleteAnnouncement(announcementId: number) {
    return request<void>(`/api/admin/announcements/${announcementId}`, {
      method: "DELETE",
    });
  },

  actionLogs(limit = 100) {
    return request<AdminActionLog[]>(
      `/api/admin/action-logs?limit=${encodeURIComponent(limit)}`,
    );
  },

  songs() {
    return request<Song[]>("/api/admin/songs");
  },

  createSong(input: {
    title: string;
    leaderUserId: number;
    leaderSessionName: string;
  }) {
    return request<Song>("/api/admin/songs", {
      method: "POST",
      body: JSON.stringify(input),
    });
  },

  renameSong(songId: number, title: string) {
    return request<Song>(`/api/admin/songs/${songId}`, {
      method: "PATCH",
      body: JSON.stringify({ title }),
    });
  },

  archiveSong(songId: number) {
    return request<Song>(`/api/admin/songs/${songId}/archive`, {
      method: "POST",
    });
  },

  restoreSong(songId: number) {
    return request<Song>(`/api/admin/songs/${songId}/restore`, {
      method: "POST",
    });
  },

  addSongMember(songId: number, userId: number, sessionName: string) {
    return request<Song>(`/api/admin/songs/${songId}/members`, {
      method: "POST",
      body: JSON.stringify({ userId, sessionName }),
    });
  },

  changeSongMemberSession(songId: number, userId: number, sessionName: string) {
    return request<Song>(`/api/admin/songs/${songId}/members/${userId}`, {
      method: "PATCH",
      body: JSON.stringify({ sessionName }),
    });
  },

  removeSongMember(songId: number, userId: number) {
    return request<Song>(`/api/admin/songs/${songId}/members/${userId}`, {
      method: "DELETE",
    });
  },

  changeSongLeader(songId: number, userId: number) {
    return request<Song>(`/api/admin/songs/${songId}/leader`, {
      method: "POST",
      body: JSON.stringify({ userId }),
    });
  },
};

export const announcementApi = {
  list() {
    return request<Announcement[]>("/api/announcements");
  },
};

export const songApi = {
  mine() {
    return request<Song[]>("/api/songs");
  },

  detail(songId: number) {
    return request<Song>(`/api/songs/${songId}`);
  },
};

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const firstFieldMessage = Object.values(error.fields)[0];
    return firstFieldMessage ?? error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "알 수 없는 오류가 발생했습니다.";
}
