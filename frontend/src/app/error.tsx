"use client";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return <main id="main" className="notice"><h1>页面暂时遇到问题</h1><p>请稍后重试。</p><button onClick={reset}>重新加载</button></main>;
}
