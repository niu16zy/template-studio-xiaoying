import Link from "next/link";
export default function NotFound() {
  return <main id="main" className="notice"><h1>页面不存在</h1><Link href="/">返回模板列表</Link></main>;
}
