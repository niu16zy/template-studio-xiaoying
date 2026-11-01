import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TaskCard } from "./task-history";
import { taskFixture as task } from "./task-fixture";

describe("task cards", () => {
  it("renders task failure independently from a temporary query exception and a successful result", () => {
    const retry = vi.fn();
    render(<>
      <TaskCard entry={{ task: task("success", { status: "SUCCEEDED", resultUrl: "/result.png" }), queryError: null, checking: false }} retry={vi.fn()} />
      <TaskCard entry={{ task: task("failure", { status: "FAILED", failureStage: "GENERATE", errorMessage: "生成器按演示设置失败" }), queryError: null, checking: false }} retry={vi.fn()} />
      <TaskCard entry={{ task: task("running", { status: "RUNNING", currentStage: "FINALIZE" }), queryError: "状态更新中断，不代表制作失败", checking: false }} retry={retry} />
    </>);
    expect(screen.getByRole("button", { name: "预览结果：旅行明信片" })).toBeInTheDocument();
    const failed = screen.getByRole("article", { name: "旅行明信片任务 failure" });
    expect(within(failed).getByText("制作失败")).toBeInTheDocument();
    expect(within(failed).getByText("生成器按演示设置失败")).toBeInTheDocument();
    const running = screen.getByRole("article", { name: "旅行明信片任务 running" });
    expect(within(running).getByText("处理中")).toBeInTheDocument();
    expect(within(running).queryByText("制作失败")).not.toBeInTheDocument();
    fireEvent.click(within(running).getByRole("button", { name: "恢复查询" })); expect(retry).toHaveBeenCalledOnce();
  });
  it("opens a labelled result dialog and restores the trigger focus when dismissed", () => {
    HTMLDialogElement.prototype.showModal = function () { this.setAttribute("open", ""); };
    HTMLDialogElement.prototype.close = function () { this.removeAttribute("open"); };
    render(<TaskCard entry={{ task: task("success", { status: "SUCCEEDED", resultUrl: "/result.png" }), queryError: null, checking: false }} retry={vi.fn()} />);
    const button = screen.getByRole("button", { name: "预览结果：旅行明信片" });
    fireEvent.click(button);
    expect(screen.getByRole("dialog", { name: "旅行明信片结果预览" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "关闭结果预览" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(); expect(button).toHaveFocus();
  });
});
