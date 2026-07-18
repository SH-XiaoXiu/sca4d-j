import { afterEach, describe, expect, it, vi } from "vitest";
import { createRealtimeClient } from "../realtime-client";

const encoder = new TextEncoder();

afterEach(() => {
  vi.useRealTimers();
});

describe("realtime client", () => {
  it("携带 Token Header 并保留服务端事件 ID", async () => {
    let streamController: ReadableStreamDefaultController<Uint8Array> | null = null;
    const request = vi.fn<typeof fetch>().mockResolvedValue(new Response(
      new ReadableStream<Uint8Array>({
        start(controller) {
          streamController = controller;
        }
      }),
      { status: 200, headers: { "Content-Type": "text/event-stream;charset=UTF-8" } }
    ));
    const onEvent = vi.fn();
    const client = createRealtimeClient({
      baseUrl: "/api/",
      token: () => ({ tokenName: "satoken", tokenValue: "token-1" }),
      onEvent,
      onReconnect: vi.fn(),
      onUnauthorized: vi.fn(),
      fetch: request
    });

    client.start();
    await vi.waitFor(() => expect(request).toHaveBeenCalledOnce());
    const [, init] = request.mock.calls[0]!;
    expect(init?.headers).toMatchObject({ Accept: "text/event-stream", satoken: "token-1" });
    streamController!.enqueue(encoder.encode(
      "id: event-7\nevent: authentication.session.changed\ndata: {}\n\n"
    ));
    await vi.waitFor(() => expect(onEvent).toHaveBeenCalledWith({
      event: "authentication.session.changed",
      data: "{}",
      id: "event-7"
    }));
    client.stop();
  });

  it("连接中断后自动重连并通知调用方全量刷新", async () => {
    vi.useFakeTimers();
    const request = vi.fn<typeof fetch>().mockImplementation(async () => new Response(
      new ReadableStream<Uint8Array>({ start(controller) { controller.close(); } }),
      { status: 200, headers: { "Content-Type": "text/event-stream" } }
    ));
    const onReconnect = vi.fn();
    const client = createRealtimeClient({
      baseUrl: "/api",
      token: () => ({ tokenName: "satoken", tokenValue: "token-1" }),
      onEvent: vi.fn(),
      onReconnect,
      onUnauthorized: vi.fn(),
      fetch: request
    });

    client.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(request).toHaveBeenCalledTimes(1);
    expect(onReconnect).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(2_000);
    expect(request).toHaveBeenCalledTimes(2);
    expect(onReconnect).toHaveBeenCalledOnce();
    client.stop();
  });

  it("401 会清理登录态并停止后续重连", async () => {
    vi.useFakeTimers();
    const request = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 401 }));
    const onUnauthorized = vi.fn();
    const client = createRealtimeClient({
      baseUrl: "/api",
      token: () => ({ tokenName: "satoken", tokenValue: "expired" }),
      onEvent: vi.fn(),
      onReconnect: vi.fn(),
      onUnauthorized,
      fetch: request
    });

    client.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(onUnauthorized).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(5_000);
    expect(request).toHaveBeenCalledOnce();
    client.stop();
  });
});
