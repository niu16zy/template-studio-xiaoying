import { TaskScope } from "@/features/tasks/task-provider";
import { TaskHistory } from "@/features/tasks/task-history";

export default function HistoryPage() {
  return <TaskScope><main id="main" className="history-page"><div className="intro"><div><p className="eyebrow">YOUR CREATIONS</p><h1>每一次灵感，都有记录</h1></div><p className="intro-note">制作完成后自动更新<br />刷新或离开页面，记录依然保留。</p></div><TaskHistory /></main></TaskScope>;
}
