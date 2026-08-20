"use client";

import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { Announcement, adminApi, errorMessage } from "@/lib/api";

type FormState = {
  id: number | null;
  title: string;
  content: string;
  pinned: boolean;
};

const EMPTY_FORM: FormState = {
  id: null,
  title: "",
  content: "",
  pinned: false,
};

export default function AdminAnnouncementsPage() {
  return (
    <AuthGate adminOnly>
      {(user) => (
        <AppShell user={user}>
          <AdminAnnouncementsContent />
        </AppShell>
      )}
    </AuthGate>
  );
}

function AdminAnnouncementsContent() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FormState>(EMPTY_FORM);

  const query = useQuery({
    queryKey: ["admin", "announcements"],
    queryFn: adminApi.announcements,
  });

  const createMutation = useMutation({
    mutationFn: adminApi.createAnnouncement,
    onSuccess: async () => {
      setForm(EMPTY_FORM);
      await invalidateAnnouncements(queryClient);
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({
      id,
      input,
    }: {
      id: number;
      input: { title: string; content: string; pinned: boolean };
    }) => adminApi.updateAnnouncement(id, input),
    onSuccess: async () => {
      setForm(EMPTY_FORM);
      await invalidateAnnouncements(queryClient);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteAnnouncement,
    onSuccess: async () => {
      setForm(EMPTY_FORM);
      await invalidateAnnouncements(queryClient);
    },
  });

  const error =
    createMutation.error ?? updateMutation.error ?? deleteMutation.error;
  const busy =
    createMutation.isPending ||
    updateMutation.isPending ||
    deleteMutation.isPending;

  function submit(event: FormEvent) {
    event.preventDefault();
    const input = {
      title: form.title.trim(),
      content: form.content.trim(),
      pinned: form.pinned,
    };
    if (form.id === null) {
      createMutation.mutate(input);
    } else {
      updateMutation.mutate({ id: form.id, input });
    }
  }

  return (
    <div className="space-y-7">
      <section>
        <p className="eyebrow">ADMIN · NOTICE</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
          공지 관리
        </h1>
        <p className="mt-3 text-sm text-slate-500">
          전체 회원에게 보이는 공지를 작성하고 관리합니다.
        </p>
      </section>

      {error && <p className="error-box">{errorMessage(error)}</p>}

      <form className="app-card space-y-4" onSubmit={submit}>
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="card-label">
              {form.id === null ? "새 공지" : "공지 수정"}
            </p>
            <h2 className="mt-2 text-lg font-bold text-slate-950">
              {form.id === null ? "공지 작성" : form.title || "제목 없음"}
            </h2>
          </div>
          {form.id !== null && (
            <button
              className="secondary-button small-button"
              type="button"
              onClick={() => setForm(EMPTY_FORM)}
            >
              새 공지로 전환
            </button>
          )}
        </div>

        <label className="field-label">
          제목
          <input
            className="field-input"
            value={form.title}
            onChange={(event) =>
              setForm((current) => ({ ...current, title: event.target.value }))
            }
            maxLength={200}
            required
          />
        </label>

        <label className="field-label">
          내용
          <textarea
            className="field-input min-h-40 resize-y"
            value={form.content}
            onChange={(event) =>
              setForm((current) => ({ ...current, content: event.target.value }))
            }
            required
          />
        </label>

        <label className="flex items-center gap-3 text-sm font-semibold text-slate-700">
          <input
            type="checkbox"
            checked={form.pinned}
            onChange={(event) =>
              setForm((current) => ({ ...current, pinned: event.target.checked }))
            }
          />
          상단 고정
        </label>

        <button
          className="primary-button"
          type="submit"
          disabled={busy || !form.title.trim() || !form.content.trim()}
        >
          {busy
            ? "저장 중..."
            : form.id === null
              ? "공지 등록"
              : "수정 저장"}
        </button>
      </form>

      <section className="space-y-3">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-xl font-bold text-slate-950">현재 공지</h2>
          <span className="count-badge">{query.data?.length ?? 0}</span>
        </div>

        {query.isPending && <Message>불러오는 중...</Message>}
        {query.isError && <p className="error-box">{errorMessage(query.error)}</p>}
        {query.data?.length === 0 && <Message>등록된 공지가 없습니다.</Message>}

        {query.data?.map((announcement) => (
          <AnnouncementRow
            key={announcement.id}
            announcement={announcement}
            busy={busy}
            onEdit={() =>
              setForm({
                id: announcement.id,
                title: announcement.title,
                content: announcement.content,
                pinned: announcement.pinned,
              })
            }
            onDelete={() => {
              if (window.confirm(`"${announcement.title}" 공지를 삭제할까요?`)) {
                deleteMutation.mutate(announcement.id);
              }
            }}
          />
        ))}
      </section>
    </div>
  );
}

function AnnouncementRow({
  announcement,
  busy,
  onEdit,
  onDelete,
}: {
  announcement: Announcement;
  busy: boolean;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <article className="app-card">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            {announcement.pinned && (
              <span className="rounded-full bg-slate-900 px-2.5 py-1 text-xs font-bold text-white">
                고정
              </span>
            )}
            <h3 className="font-bold text-slate-950">{announcement.title}</h3>
          </div>
          <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-600">
            {announcement.content}
          </p>
          <p className="mt-3 text-xs text-slate-400">
            {announcement.authorName} · {formatDate(announcement.updatedAt)}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            className="secondary-button small-button"
            type="button"
            disabled={busy}
            onClick={onEdit}
          >
            수정
          </button>
          <button
            className="danger-button small-button"
            type="button"
            disabled={busy}
            onClick={onDelete}
          >
            삭제
          </button>
        </div>
      </div>
    </article>
  );
}

async function invalidateAnnouncements(
  queryClient: ReturnType<typeof useQueryClient>,
) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ["admin", "announcements"] }),
    queryClient.invalidateQueries({ queryKey: ["announcements"] }),
    queryClient.invalidateQueries({ queryKey: ["admin", "action-logs"] }),
  ]);
}

function Message({ children }: { children: React.ReactNode }) {
  return (
    <p className="app-card text-center text-sm text-slate-400">{children}</p>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
