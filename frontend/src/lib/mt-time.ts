/**
 * Submission timestamps are TIMESTAMPTZ values provided by the backend.
 * Always show them in Korean local time, even when the participant's device
 * is configured for a different timezone.
 */
const mtClock = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Seoul",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

export function formatMtTime(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "--:--" : mtClock.format(date);
}
