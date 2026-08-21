self.addEventListener("push", (event) => {
  let payload = {
    title: "합주 알림",
    body: "새 알림이 도착했습니다.",
    linkPath: "/notifications",
    tag: "rehearsal-notification",
  };

  if (event.data) {
    try {
      payload = { ...payload, ...event.data.json() };
    } catch {
      payload.body = event.data.text();
    }
  }

  event.waitUntil(
    self.registration.showNotification(payload.title, {
      body: payload.body,
      tag: payload.tag,
      data: {
        linkPath: payload.linkPath || "/notifications",
      },
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();

  const linkPath =
    event.notification.data?.linkPath || "/notifications";
  const targetUrl = new URL(linkPath, self.location.origin).href;

  event.waitUntil(
    clients
      .matchAll({
        type: "window",
        includeUncontrolled: true,
      })
      .then(async (clientList) => {
        for (const client of clientList) {
          if ("navigate" in client) {
            await client.navigate(targetUrl);
          }
          if ("focus" in client) {
            return client.focus();
          }
        }

        if (clients.openWindow) {
          return clients.openWindow(targetUrl);
        }
      }),
  );
});
