import { beforeEach, describe, expect, it, vi } from "vitest";

const user = {
  id: 1,
  loginId: "tester",
  name: "테스터",
  clubId: 1,
  role: "MEMBER" as const,
};

function jsonResponse(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("request", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.unstubAllGlobals();
  });

  it("sends credentialed requests and retries once after refresh", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse(user))
      .mockResolvedValueOnce(jsonResponse(user));

    vi.stubGlobal("fetch", fetchMock);

    const { request } = await import("./api");
    await expect(request<typeof user>("/api/auth/me")).resolves.toEqual(user);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/api\/auth\/me$/);
    expect(fetchMock.mock.calls[0][1]).toEqual(
      expect.objectContaining({ credentials: "include" }),
    );
    expect(String(fetchMock.mock.calls[1][0])).toMatch(/\/api\/auth\/refresh$/);
    expect(String(fetchMock.mock.calls[2][0])).toMatch(/\/api\/auth\/me$/);
  });

  it("shares one refresh request across concurrent 401 responses", async () => {
    let protectedCalls = 0;
    let refreshCalls = 0;

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);

      if (url.endsWith("/api/auth/refresh")) {
        refreshCalls += 1;
        await Promise.resolve();
        return jsonResponse(user);
      }

      protectedCalls += 1;
      if (protectedCalls <= 2) {
        return new Response(null, { status: 401 });
      }
      return jsonResponse(user);
    });

    vi.stubGlobal("fetch", fetchMock);

    const { request } = await import("./api");
    const [first, second] = await Promise.all([
      request<typeof user>("/api/auth/me"),
      request<typeof user>("/api/auth/me"),
    ]);

    expect(first).toEqual(user);
    expect(second).toEqual(user);
    expect(refreshCalls).toBe(1);
    expect(protectedCalls).toBe(4);
  });

  it("preserves backend error code and field validation message", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            code: "VALIDATION_ERROR",
            message: "입력값을 확인해주세요.",
            fields: { loginId: "아이디 형식이 올바르지 않습니다." },
          },
          400,
        ),
      ),
    );

    const { ApiError, errorMessage, request } = await import("./api");

    let caught: unknown;
    try {
      await request("/api/auth/login", { method: "POST" }, false);
    } catch (error) {
      caught = error;
    }

    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as InstanceType<typeof ApiError>).code).toBe(
      "VALIDATION_ERROR",
    );
    expect(errorMessage(caught)).toBe("아이디 형식이 올바르지 않습니다.");
  });
});
