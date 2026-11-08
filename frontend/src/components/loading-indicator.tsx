"use client";

export function LoadingIndicator({
  label,
  compact = false,
}: {
  label: string;
  compact?: boolean;
}) {
  return (
    <div
      role="status"
      aria-label={label}
      className={`flex items-center gap-3 text-sm text-slate-500 ${compact ? "" : "min-h-12"}`}
    >
      <span className="loading-indicator-visual" aria-hidden="true">
        <svg
          viewBox="0 0 24 24"
          className="h-5 w-5 animate-spin text-slate-600"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.4"
          strokeLinecap="round"
        >
          <path d="M12 3a9 9 0 1 1-9 9" />
        </svg>
      </span>
      <span>{label}</span>
    </div>
  );
}
