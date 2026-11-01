import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Template } from "@/lib/contracts";
import { UploadWorkspace } from "./upload-workspace";
import { taskFixture } from "@/features/tasks/task-fixture";

const { accept, refresh } = vi.hoisted(() => ({ accept: vi.fn(), refresh: vi.fn() }));
vi.mock("@/features/tasks/task-provider", () => ({ useTasks: () => ({ store: { accept, refresh } }) }));

vi.mock("@/features/session/session-provider", () => ({
  useSession: () => ({ checking: false, refresh: vi.fn() }),
}));
const template: Template = { id: "one", categoryId: "c", title: "One", description: "", coverUrl: "", sampleUrl: "",
  requiredImageCount: 1, acceptedMimeTypes: ["image/png"], maxFileSizeBytes: 5242880 };
beforeEach(() => {
  Object.defineProperty(URL, "createObjectURL", { configurable: true, value: vi.fn(() => "blob:preview") });
  Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: vi.fn() });
});

describe("task submission controls", () => {
  it("requires a finished upload, accepts only once while locked and enables the next draft after 202", async () => {
    let finishValidation!: (response: Response) => void;
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "asset-1", previewUrl: "/api/assets/asset-1/content", mimeType: "image/png", sizeBytes: 3, width: 1, height: 1 })))
      .mockReturnValueOnce(new Promise<Response>((resolve) => { finishValidation = resolve; }));
    vi.stubGlobal("fetch", fetcher);
    render(<UploadWorkspace template={template} userId="a" />);
    expect(screen.getByRole("button", { name: "开始制作" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("选择或替换素材 1"), {
      target: { files: [new File(["png"], "photo.png", { type: "image/png" })] },
    });
    await waitFor(() => expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
    fireEvent.click(screen.getByRole("button", { name: "正在提交…" }));
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(screen.getByLabelText("选择或替换素材 1")).toBeDisabled();
    expect(fetcher.mock.calls[1][0]).toBe("/api/tasks");
    const task = taskFixture("accepted");
    await act(async () => { finishValidation(new Response(JSON.stringify(task), { status: 202 })); });
    expect(accept).toHaveBeenCalledWith(task);
    expect(screen.getByText(/任务已受理/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "开始制作" })).toBeDisabled();
    expect(screen.getByLabelText("选择或替换素材 1")).toBeEnabled();
  });

  it("does not automatically repeat an uncertain submission and asks to reconcile history", async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ id: "asset-1" }))).mockRejectedValueOnce(new TypeError("Network failed"));
    vi.stubGlobal("fetch", fetcher);
    render(<UploadWorkspace template={template} userId="a" />);
    fireEvent.change(screen.getByLabelText("选择或替换素材 1"), { target: { files: [new File(["png"], "photo.png", { type: "image/png" })] } });
    await waitFor(() => expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "开始制作" }));
    await screen.findByText(/未能确认提交结果/);
    expect(screen.getByRole("button", { name: "开始制作" })).toBeDisabled();
    expect(screen.getByLabelText("选择或替换素材 1")).toBeDisabled();
    expect(refresh).toHaveBeenCalledOnce(); expect(accept).not.toHaveBeenCalled(); expect(fetcher).toHaveBeenCalledTimes(2);
    fireEvent.click(screen.getByRole("button", { name: "已核对记录，允许再次提交" }));
    expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled();
  });

  it("ignores a submitted response after the identity scope unmounts", async () => {
    let finish!: (response: Response) => void;
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ id: "asset-1" }))).mockReturnValueOnce(new Promise<Response>((resolve) => { finish = resolve; })));
    const view = render(<UploadWorkspace template={template} userId="a" />);
    fireEvent.change(screen.getByLabelText("选择或替换素材 1"), { target: { files: [new File(["png"], "photo.png", { type: "image/png" })] } });
    await waitFor(() => expect(screen.getByRole("button", { name: "开始制作" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "开始制作" })); view.unmount();
    await act(async () => finish(new Response(JSON.stringify(taskFixture("old")), { status: 202 })));
    expect(accept).not.toHaveBeenCalled();
  });
});
