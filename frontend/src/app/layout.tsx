import type { Metadata } from "next";
import Link from "next/link";
import { SessionProvider } from "@/features/session/session-provider";
import { SessionMenu } from "@/features/session/session-menu";
import "./globals.css";

export const metadata: Metadata = {
  title: "拾影 · 素材工作室",
  description: "把日常灵感，做成值得收藏的作品。",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>
        <SessionProvider>
        <a className="skip-link" href="#main">跳到主要内容</a>
        <header className="site-header">
          <Link href="/" className="brand" aria-label="拾影首页">
            <span className="brand-mark" aria-hidden="true">✳</span>
            <span>拾影<span className="brand-en">STUDIO</span></span>
          </Link>
          <nav aria-label="主导航"><Link href="/">模板灵感</Link><Link href="/history">我的记录</Link></nav>
          <SessionMenu />
        </header>
        {children}
        <footer>拾影 STUDIO <span>本地示例素材 · 模拟制作项目</span></footer>
        </SessionProvider>
      </body>
    </html>
  );
}
