"use client";

import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { NotificationSettingsCard } from "@/components/notification-settings";

export default function MyNotificationsPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <div className="space-y-7">
            <section>
              <p className="eyebrow">ALL · NOTIFICATIONS</p>
              <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
                알림 설정
              </h1>
            </section>

            <NotificationSettingsCard />
          </div>
        </AppShell>
      )}
    </AuthGate>
  );
}
