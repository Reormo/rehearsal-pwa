"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useMemo, useRef, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import {
  AuthUser,
  errorMessage,
  songApi,
} from "@/lib/api";
import {
  BookableScheduleSlot,
  BookingRoundState,
  BookingTimeOption,
  Reservation,
  ScheduleDaySummary,
  scheduleApi,
  UnavailableScheduleSlot,
} from "@/lib/schedule-api";

const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];
const ALLOWED_DURATIONS = [30, 60, 90, 120, 150, 180];

export default function SchedulePage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <ScheduleContent user={user} />
        </AppShell>
      )}
    </AuthGate>
  );
}

function ScheduleContent({ user }: { user: AuthUser }) {
  const queryClient = useQueryClient();
  const today = useMemo(() => new Date(), []);
  const [month, setMonth] = useState(
    () => new Date(today.getFullYear(), today.getMonth(), 1),
  );
  const [selectedDate, setSelectedDate] = useState(() => toIsoDate(today));
  const [selectedSongId, setSelectedSongId] = useState<number | null>(null);
  const [durationMinutes, setDurationMinutes] = useState(30);
  const [selectedStartAt, setSelectedStartAt] = useState<string | null>(null);
  const [bookingMessage, setBookingMessage] = useState<string | null>(null);
  const touchStartX = useRef<number | null>(null);

  const leaderSongsQuery = useQuery({
    queryKey: ["songs", "mine"],
    queryFn: songApi.mine,
  });
  const leaderSongs = useMemo(
    () =>
      (leaderSongsQuery.data ?? []).filter((song) =>
        song.members.some(
          (member) => member.userId === user.id && member.leader,
        ),
      ),
    [leaderSongsQuery.data, user.id],
  );
  const effectiveSongId =
    leaderSongs.some((song) => song.id === selectedSongId)
      ? selectedSongId
      : (leaderSongs[0]?.id ?? null);

  const range = useMemo(() => calendarRange(month), [month]);
  const calendarQuery = useQuery({
    queryKey: ["schedule", "calendar", range.from, range.to],
    queryFn: () => scheduleApi.calendar(range.from, range.to),
  });
  const dayMap = useMemo(
    () =>
      new Map(
        (calendarQuery.data?.days ?? []).map((day) => [day.date, day] as const),
      ),
    [calendarQuery.data],
  );
  const selectedSummary = dayMap.get(selectedDate);
  const dayQuery = useQuery({
    queryKey: ["schedule", "day", selectedDate],
    queryFn: () => scheduleApi.day(selectedDate),
    enabled: Boolean(selectedSummary?.roundId),
  });

  const allowedDurations = useMemo(
    () =>
      ALLOWED_DURATIONS.filter(
        (duration) => duration <= (dayQuery.data?.round.maxReservationMinutes ?? 0),
      ),
    [dayQuery.data?.round.maxReservationMinutes],
  );
  const effectiveDuration = allowedDurations.includes(durationMinutes)
    ? durationMinutes
    : (allowedDurations[0] ?? 30);

  const bookingOptionsQuery = useQuery({
    queryKey: [
      "schedule",
      "booking-options",
      selectedDate,
      effectiveDuration,
    ],
    queryFn: () =>
      scheduleApi.bookingOptions(selectedDate, effectiveDuration),
    enabled: Boolean(dayQuery.data?.round.id && effectiveSongId),
  });
  const effectiveStartAt = bookingOptionsQuery.data?.options.some(
    (option) => option.startAt === selectedStartAt,
  )
    ? selectedStartAt
    : null;

  const myReservationsQuery = useQuery({
    queryKey: ["schedule", "my-reservations"],
    queryFn: scheduleApi.myReservations,
  });

  const bookingMutation = useMutation({
    mutationFn: scheduleApi.createReservation,
    onSuccess: async (reservation) => {
      setSelectedStartAt(null);
      setBookingMessage(
        `${reservation.songTitle} · ${formatTime(reservation.startAt)} 예약이 완료되었습니다.`,
      );
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["schedule", "day", selectedDate],
        }),
        queryClient.invalidateQueries({
          queryKey: [
            "schedule",
            "booking-options",
            selectedDate,
            effectiveDuration,
          ],
        }),
        queryClient.invalidateQueries({
          queryKey: ["schedule", "my-reservations"],
        }),
      ]);
    },
  });

  function clearBookingFeedback() {
    bookingMutation.reset();
    setBookingMessage(null);
  }

  function moveMonth(delta: number) {
    const next = new Date(month.getFullYear(), month.getMonth() + delta, 1);
    setMonth(next);
    setSelectedDate(toIsoDate(next));
    setSelectedStartAt(null);
    clearBookingFeedback();
  }

  function selectDate(date: string) {
    setSelectedDate(date);
    setSelectedStartAt(null);
    clearBookingFeedback();
  }

  return (
    <div className="space-y-7">
      <section>
        <p className="eyebrow">SCHEDULE</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
          합주 예약 · 시간표
        </h1>
        <p className="mt-3 text-sm leading-6 text-slate-500">
          시간표는 30분 원자 슬롯으로 관리합니다. 팀장이 예약 길이를 고르면 실제
          예약 시작 시각은 30분 간격으로 다시 계산하고, 서버가 요청 순간 빈 슬롯을
          잠가 선착순을 확정합니다.
        </p>
      </section>

      <section className="app-card">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="card-label">1. 팀 선택</p>
            <h2 className="mt-2 text-xl font-bold text-slate-950">
              예약할 팀
            </h2>
          </div>
          <span className="count-badge">팀장 {leaderSongs.length}팀</span>
        </div>

        {leaderSongsQuery.isPending && (
          <p className="mt-4 text-sm text-slate-400">팀 정보를 불러오는 중...</p>
        )}
        {leaderSongsQuery.isError && (
          <p className="error-box mt-4">{errorMessage(leaderSongsQuery.error)}</p>
        )}
        {!leaderSongsQuery.isPending && leaderSongs.length === 0 ? (
          <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
            현재 팀장으로 지정된 활성 팀이 없습니다. 일반 시간표는 아래에서 계속
            확인할 수 있습니다.
          </p>
        ) : (
          <select
            className="field-input mt-4"
            value={effectiveSongId ?? ""}
            onChange={(event) => {
              setSelectedSongId(Number(event.target.value));
              setSelectedStartAt(null);
              clearBookingFeedback();
            }}
          >
            {leaderSongs.map((song) => (
              <option key={song.id} value={song.id}>
                {song.title}
              </option>
            ))}
          </select>
        )}
      </section>

      <section
        className="app-card select-none"
        onTouchStart={(event) => {
          touchStartX.current = event.touches[0]?.clientX ?? null;
        }}
        onTouchEnd={(event) => {
          if (touchStartX.current == null) return;
          const endX = event.changedTouches[0]?.clientX ?? touchStartX.current;
          const delta = endX - touchStartX.current;
          touchStartX.current = null;
          if (Math.abs(delta) < 50) return;
          moveMonth(delta < 0 ? 1 : -1);
        }}
      >
        <div className="flex items-center justify-between gap-4">
          <button
            type="button"
            className="secondary-button small-button"
            onClick={() => moveMonth(-1)}
          >
            &lt;
          </button>
          <div className="text-center">
            <p className="card-label">2. 날짜 선택</p>
            <h2 className="mt-1 text-lg font-bold text-slate-950">
              {month.getFullYear()}년 {month.getMonth() + 1}월
            </h2>
          </div>
          <button
            type="button"
            className="secondary-button small-button"
            onClick={() => moveMonth(1)}
          >
            &gt;
          </button>
        </div>

        {calendarQuery.isError && (
          <p className="error-box mt-4">{errorMessage(calendarQuery.error)}</p>
        )}

        <div className="mt-5 grid grid-cols-7 gap-1 text-center">
          {WEEKDAYS.map((weekday) => (
            <div
              key={weekday}
              className="py-2 text-xs font-bold text-slate-400"
            >
              {weekday}
            </div>
          ))}
          {range.dates.map((date) => {
            const iso = toIsoDate(date);
            const summary = dayMap.get(iso);
            return (
              <CalendarDay
                key={iso}
                date={date}
                currentMonth={month.getMonth()}
                selected={selectedDate === iso}
                summary={summary}
                onClick={() => selectDate(iso)}
              />
            );
          })}
        </div>
      </section>

      <section className="app-card">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="card-label">선택한 날짜</p>
            <h2 className="mt-2 text-xl font-bold text-slate-950">
              {formatDateLabel(selectedDate)}
            </h2>
          </div>
          {dayQuery.data && (
            <span className="count-badge">
              {roundStateLabel(dayQuery.data.round.state)}
            </span>
          )}
        </div>

        {!selectedSummary?.roundId && !calendarQuery.isPending && (
          <p className="mt-5 rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-400">
            아직 예약 회차가 준비되지 않은 날짜입니다.
          </p>
        )}
        {dayQuery.isPending && dayQuery.fetchStatus !== "idle" && (
          <p className="mt-5 text-sm text-slate-400">시간표를 불러오는 중...</p>
        )}
        {dayQuery.isError && (
          <p className="error-box mt-5">{errorMessage(dayQuery.error)}</p>
        )}
        {dayQuery.data && (
          <>
            <div className="mt-4 rounded-2xl bg-slate-50 p-4 text-sm">
              <p className="font-semibold text-slate-800">
                {dayQuery.data.round.roundNo}회차 ·{" "}
                {formatDateLabel(dayQuery.data.round.startDate)} ~{" "}
                {formatDateLabel(dayQuery.data.round.endDate)}
              </p>
              <p className="mt-1 text-slate-500">
                예약 오픈 {formatDateTime(dayQuery.data.round.bookingOpenAt)}
              </p>
              <p className="mt-1 text-slate-500">
                1회 최대 {dayQuery.data.round.maxReservationMinutes}분 · 일반 슬롯{" "}
                {dayQuery.data.standardSlots.length}개
              </p>
              {dayQuery.data.roomStatus === "PARTIAL_BLOCKED" && (
                <p className="mt-2 font-semibold text-amber-700">
                  일부 시간 사용 불가
                </p>
              )}
              {dayQuery.data.roomStatus === "CLOSED" && (
                <p className="mt-2 font-semibold text-red-600">
                  오늘은 전체 시간 사용 불가
                </p>
              )}
            </div>

            {dayQuery.data.blockedPeriods.length > 0 && (
              <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4">
                <p className="text-sm font-bold text-amber-900">사용 불가 시간</p>
                <div className="mt-2 space-y-1 text-sm text-amber-800">
                  {dayQuery.data.blockedPeriods.map((period) => (
                    <p key={period.id}>
                      {trimTime(period.blockedStartTime)} ~{" "}
                      {trimTime(period.blockedEndTime)} · {period.reason}
                    </p>
                  ))}
                </div>
              </div>
            )}

            <div className="mt-6 border-t border-slate-200 pt-6">
              <div>
                <p className="card-label">3. 예약 길이 선택</p>
                <p className="mt-1 text-sm text-slate-500">
                  이 회차는 최대 {dayQuery.data.round.maxReservationMinutes}분까지 예약할
                  수 있습니다.
                </p>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {allowedDurations.map((duration) => (
                  <button
                    key={duration}
                    type="button"
                    className={
                      effectiveDuration === duration
                        ? "primary-button small-button"
                        : "secondary-button small-button"
                    }
                    onClick={() => {
                      setDurationMinutes(duration);
                      setSelectedStartAt(null);
                      clearBookingFeedback();
                    }}
                  >
                    {duration}분
                  </button>
                ))}
              </div>
            </div>

            <div className="mt-6">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="card-label">4. 시간 선택</p>
                  <p className="mt-1 text-sm leading-6 text-slate-500">
                    일반 예약 슬롯, 잔여 슬롯, 예약할 수 없는 시간을 한 시간표에서
                    확인하고 선택한 {effectiveDuration}분 예약의 시작 시각을 고릅니다.
                  </p>
                </div>
                {bookingOptionsQuery.data && (
                  <span className="count-badge">
                    시작 가능 {bookingOptionsQuery.data.options.length}개
                  </span>
                )}
              </div>

              {!effectiveSongId && (
                <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-5 text-center text-sm text-slate-500">
                  시간표는 확인할 수 있지만 예약하려면 먼저 팀장으로 지정된 팀이
                  필요합니다.
                </p>
              )}
              {effectiveSongId && bookingOptionsQuery.isPending && (
                <p className="mt-4 text-sm text-slate-400">
                  예약 가능한 시작 시간을 계산하는 중...
                </p>
              )}
              {bookingOptionsQuery.isError && (
                <p className="error-box mt-4">
                  {errorMessage(bookingOptionsQuery.error)}
                </p>
              )}
              {bookingOptionsQuery.data &&
                !bookingOptionsQuery.data.acceptingReservations && (
                  <p className="mt-4 rounded-2xl bg-amber-50 px-4 py-4 text-sm font-semibold text-amber-800">
                    현재 이 회차는 예약 접수 시간이 아닙니다. 시간표는 볼 수 있지만
                    새 예약은 서버에서 허용하지 않습니다.
                  </p>
                )}
              {bookingOptionsQuery.data?.acceptingReservations &&
                bookingOptionsQuery.data.options.length === 0 && (
                  <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-4 text-sm text-slate-500">
                    선택한 길이로 바로 예약 가능한 미래 시작 시각은 없습니다.
                  </p>
                )}

              <div className="mt-5">
                <p className="card-label">일반 예약 슬롯</p>
                <p className="mt-1 text-sm text-slate-500">
                  선택한 {effectiveDuration}분 예약의 시작 시간을 선택하세요.
                </p>
                {bookingOptionsForSlots(
                  dayQuery.data.standardSlots,
                  bookingOptionsQuery.data?.options ?? [],
                ).length === 0 ? (
                  <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-400">
                    일반 구간에서 선택한 길이로 시작할 수 있는 시간이 없습니다.
                  </p>
                ) : (
                  <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3">
                    {bookingOptionsForSlots(
                      dayQuery.data.standardSlots,
                      bookingOptionsQuery.data?.options ?? [],
                    ).map((option) => (
                      <BookingTimeOptionButton
                        key={option.startAt}
                        option={option}
                        selected={effectiveStartAt === option.startAt}
                        disabled={
                          !(bookingOptionsQuery.data?.acceptingReservations ?? false)
                        }
                        onSelect={() => {
                          setSelectedStartAt(option.startAt);
                          clearBookingFeedback();
                        }}
                      />
                    ))}
                  </div>
                )}
              </div>

              {dayQuery.data.remainderSlots.length > 0 && (
                <div className="mt-6 border-t border-slate-200 pt-6">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="card-label">잔여 슬롯</p>
                      <p className="mt-1 text-sm leading-6 text-slate-500">
                        예약 또는 사용 불가 시간 때문에 최대 길이로 묶이지 못한 빈
                        구간입니다. 선택한 길이로 가능한 시작 시각만 아래에 표시됩니다.
                      </p>
                    </div>
                    <span className="count-badge">
                      {dayQuery.data.remainderSlots.length}
                    </span>
                  </div>
                  <div className="mt-4 grid gap-3 sm:grid-cols-2">
                    {dayQuery.data.remainderSlots.map((slot) => (
                      <BookableSlotRow
                        key={`remainder-${slot.startAt}-${slot.endAt}`}
                        slot={slot}
                        remainder
                        durationMinutes={effectiveDuration}
                        options={bookingOptionsForSlot(
                          slot,
                          bookingOptionsQuery.data?.options ?? [],
                        )}
                        selectedStartAt={effectiveStartAt}
                        acceptingReservations={
                          bookingOptionsQuery.data?.acceptingReservations ?? false
                        }
                        onSelect={(startAt) => {
                          setSelectedStartAt(startAt);
                          clearBookingFeedback();
                        }}
                      />
                    ))}
                  </div>
                </div>
              )}

              {dayQuery.data.unavailableSlots.length > 0 && (
                <div className="mt-6 border-t border-slate-200 pt-6">
                  <p className="card-label">예약할 수 없는 시간</p>
                  <div className="mt-3 grid gap-2 sm:grid-cols-2">
                    {dayQuery.data.unavailableSlots.map((slot) => (
                      <UnavailableSlotRow
                        key={`${slot.startAt}-${slot.endAt}-${slot.state}`}
                        slot={slot}
                      />
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div className="mt-6 rounded-2xl border border-slate-200 p-4">
              <p className="card-label">5. 예약 요청</p>
              <p className="mt-2 text-sm leading-6 text-slate-500">
                버튼을 누른 순간 서버가 팀과 30분 원자 슬롯을 다시 확인하고 잠급니다.
                화면에 보였던 시간이더라도 다른 팀이 먼저 확정했다면 예약은 실패할 수
                있습니다.
              </p>
              {bookingMessage && (
                <p className="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-700">{bookingMessage}</p>
              )}
              {bookingMutation.isError && (
                <p className="error-box mt-4">
                  {errorMessage(bookingMutation.error)}
                </p>
              )}
              <button
                type="button"
                className="primary-button mt-4 w-full"
                disabled={
                  !effectiveSongId ||
                  !effectiveStartAt ||
                  !bookingOptionsQuery.data?.acceptingReservations ||
                  bookingMutation.isPending
                }
                onClick={() => {
                  if (!effectiveSongId || !effectiveStartAt) return;
                  setBookingMessage(null);
                  bookingMutation.mutate({
                    songId: effectiveSongId,
                    startAt: effectiveStartAt,
                    durationMinutes: effectiveDuration,
                  });
                }}
              >
                {bookingMutation.isPending ? "예약 확정 중..." : "선착순 예약 확정"}
              </button>
            </div>

          </>
        )}
      </section>

      <section className="app-card">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="card-label">MY REHEARSALS</p>
            <h2 className="mt-2 text-xl font-bold text-slate-950">
              내 예정 합주
            </h2>
          </div>
          {myReservationsQuery.data && (
            <span className="count-badge">{myReservationsQuery.data.length}</span>
          )}
        </div>
        {myReservationsQuery.isPending && (
          <p className="mt-4 text-sm text-slate-400">예정 합주를 불러오는 중...</p>
        )}
        {myReservationsQuery.isError && (
          <p className="error-box mt-4">
            {errorMessage(myReservationsQuery.error)}
          </p>
        )}
        {myReservationsQuery.data?.length === 0 && (
          <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
            참여 중인 팀의 예정 합주가 없습니다.
          </p>
        )}
        {myReservationsQuery.data && myReservationsQuery.data.length > 0 && (
          <div className="mt-4 space-y-2">
            {myReservationsQuery.data.map((reservation) => (
              <ReservationRow key={reservation.id} reservation={reservation} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function CalendarDay({
  date,
  currentMonth,
  selected,
  summary,
  onClick,
}: {
  date: Date;
  currentMonth: number;
  selected: boolean;
  summary?: ScheduleDaySummary;
  onClick: () => void;
}) {
  const outside = date.getMonth() !== currentMonth;
  const closed = summary?.roomStatus === "CLOSED";
  const partial = summary?.roomStatus === "PARTIAL_BLOCKED";
  const prepared = Boolean(summary?.roundId);

  return (
    <button
      type="button"
      disabled={!prepared}
      onClick={onClick}
      className={`min-h-16 rounded-xl border px-1 py-2 text-left transition ${
        selected
          ? "border-slate-950 bg-slate-950 text-white"
          : "border-slate-100 bg-white hover:border-slate-300"
      } ${outside && !selected ? "opacity-35" : ""} ${
        !prepared ? "cursor-not-allowed opacity-25" : ""
      }`}
    >
      <span className="text-xs font-bold">{date.getDate()}</span>
      <span
        className={`mt-1 block truncate text-[10px] font-semibold ${
          selected
            ? "text-white/80"
            : closed
              ? "text-red-500"
              : partial
                ? "text-amber-600"
                : "text-slate-400"
        }`}
      >
        {closed
          ? "사용 불가"
          : partial
            ? `${summary?.blockedPeriodCount ?? 0}개 예외`
            : prepared
              ? "10~22"
              : "준비 전"}
      </span>
    </button>
  );
}

function BookingTimeOptionButton({
  option,
  selected,
  disabled,
  onSelect,
}: {
  option: BookingTimeOption;
  selected: boolean;
  disabled: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onSelect}
      className={`min-h-20 rounded-2xl border px-4 py-3 text-center transition ${
        selected
          ? "border-slate-950 bg-slate-950 text-white"
          : "border-slate-200 bg-white hover:border-slate-400"
      } disabled:cursor-not-allowed disabled:opacity-40`}
    >
      <span className="block text-lg font-extrabold tracking-tight">
        {formatTime(option.startAt)}
      </span>
      <span
        className={`mt-1 block text-xs font-semibold ${
          selected ? "text-white/70" : "text-slate-400"
        }`}
      >
        ~ {formatTime(option.endAt)}
      </span>
    </button>
  );
}

function BookableSlotRow({
  slot,
  remainder = false,
  durationMinutes,
  options,
  selectedStartAt,
  acceptingReservations,
  onSelect,
}: {
  slot: BookableScheduleSlot;
  remainder?: boolean;
  durationMinutes: number;
  options: BookingTimeOption[];
  selectedStartAt: string | null;
  acceptingReservations: boolean;
  onSelect: (startAt: string) => void;
}) {
  return (
    <div className="rounded-2xl border border-slate-200 px-4 py-3">
      <div className="flex items-center justify-between gap-3">
        <span className="font-semibold text-slate-900">
          {formatTime(slot.startAt)} ~ {formatTime(slot.endAt)}
        </span>
        <span
          className={
            remainder
              ? "text-xs font-bold text-amber-700"
              : "text-xs font-bold text-emerald-600"
          }
        >
          {remainder
            ? `잔여 ${slot.durationMinutes}분`
            : `${slot.durationMinutes}분 빈 구간`}
        </span>
      </div>
      {options.length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-2">
          {options.map((option) => {
            const selected = selectedStartAt === option.startAt;
            return (
              <button
                key={option.startAt}
                type="button"
                disabled={!acceptingReservations}
                className={
                  selected
                    ? "primary-button small-button"
                    : "secondary-button small-button"
                }
                onClick={() => onSelect(option.startAt)}
              >
                {formatTime(option.startAt)} ~ {formatTime(option.endAt)}
              </button>
            );
          })}
        </div>
      ) : (
        <p className="mt-2 text-xs text-slate-400">
          선택한 {durationMinutes}분으로 시작할 수 있는 시간이 없습니다.
        </p>
      )}
    </div>
  );
}

function UnavailableSlotRow({ slot }: { slot: UnavailableScheduleSlot }) {
  const reserved = slot.state === "RESERVED";
  return (
    <div className="flex items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
      <span className="font-semibold text-slate-700">
        {formatTime(slot.startAt)} ~ {formatTime(slot.endAt)}
      </span>
      <span
        className={`text-right text-xs font-bold ${
          reserved ? "text-slate-600" : "text-red-500"
        }`}
      >
        {reserved
          ? `${slot.songTitle ?? "다른 팀"} · 예약됨`
          : "사용 불가"}
      </span>
    </div>
  );
}

function ReservationRow({ reservation }: { reservation: Reservation }) {
  return (
    <div className="rounded-2xl border border-slate-200 px-4 py-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-bold text-slate-900">{reservation.songTitle}</p>
          <p className="mt-1 text-sm text-slate-500">
            {formatReservationDate(reservation.startAt)} ·{" "}
            {formatTime(reservation.startAt)} ~ {formatTime(reservation.endAt)}
          </p>
        </div>
        <span className="count-badge">
          {minutesBetween(reservation.startAt, reservation.endAt)}분
        </span>
      </div>
    </div>
  );
}

function bookingOptionsForSlots(
  slots: BookableScheduleSlot[],
  options: BookingTimeOption[],
) {
  return slots.flatMap((slot) => bookingOptionsForSlot(slot, options));
}

function bookingOptionsForSlot(
  slot: BookableScheduleSlot,
  options: BookingTimeOption[],
) {
  const slotStart = new Date(slot.startAt).getTime();
  const slotEnd = new Date(slot.endAt).getTime();
  return options.filter((option) => {
    const optionStart = new Date(option.startAt).getTime();
    return optionStart >= slotStart && optionStart < slotEnd;
  });
}

function calendarRange(month: Date) {
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const mondayOffset = (first.getDay() + 6) % 7;
  const start = new Date(
    first.getFullYear(),
    first.getMonth(),
    first.getDate() - mondayOffset,
  );
  const dates = Array.from({ length: 42 }, (_, index) => {
    return new Date(
      start.getFullYear(),
      start.getMonth(),
      start.getDate() + index,
    );
  });
  return {
    from: toIsoDate(dates[0]),
    to: toIsoDate(dates[dates.length - 1]),
    dates,
  };
}

function toIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatDateLabel(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    weekday: "short",
  }).format(new Date(year, month - 1, day));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "short",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatReservationDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "short",
    day: "numeric",
    weekday: "short",
  }).format(new Date(value));
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function trimTime(value: string) {
  return value.slice(0, 5);
}

function minutesBetween(startAt: string, endAt: string) {
  return Math.round(
    (new Date(endAt).getTime() - new Date(startAt).getTime()) / 60_000,
  );
}

function roundStateLabel(state: BookingRoundState) {
  if (state === "UPCOMING") return "오픈 전";
  if (state === "BOOKING_OPEN") return "예약 접수 중";
  if (state === "IN_PROGRESS") return "진행 중";
  return "마감";
}
